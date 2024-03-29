package org.example.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD;

public class EmailSender {
    private Properties sessionProps;
    private Properties librarySenderProps;
    private static final String SMTP_HOST = "smtp.gmail.com";

    public EmailSender() {
        sessionProps = new Properties();
        librarySenderProps = new Properties();
    }

    public void setProps() {
        try {
            sessionProps.load(getClass().getClassLoader().getResourceAsStream("files/session.properties"));
            librarySenderProps.load(getClass().getClassLoader().getResourceAsStream("files/librarySender.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPdfReport(String header, String text, List<Person> to) {
        ByteArrayOutputStream pdfOutputStream = generatePdfReport(header, text);

        Session session = Session.getInstance(sessionProps, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(librarySenderProps.getProperty("email"),
                        librarySenderProps.getProperty("password"));
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(librarySenderProps.getProperty("email")));
            message.setSubject(header);

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(text);
            multipart.addBodyPart(textPart);

            MimeBodyPart pdfAttachment = new MimeBodyPart();
            pdfAttachment.setFileName("report.pdf");
            pdfAttachment.setContent(pdfOutputStream.toByteArray(), "application/pdf");
            multipart.addBodyPart(pdfAttachment);

            for (Person person : to) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(person.getEmail()));
            }

            message.setContent(multipart);

            Transport.send(message);
            System.out.println("PDF report email sent!");

        } catch (MessagingException e) {
            e.printStackTrace();
            System.out.println("Error sending email");
        } finally {
            try {
                pdfOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ByteArrayOutputStream generatePdfReport(String header, String text) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(header);
                contentStream.newLine();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.showText(text);
                contentStream.endText();

                // Fetch data from the database
                String jdbcURL = "jdbc:your_database_url";
                String username = "your_username";
                String password = "your_password";
                try (Connection connection = DriverManager.getConnection(jdbcURL, username, password)) {
                    String query = "SELECT name, email FROM person";
                    try (Statement statement = connection.createStatement();
                         ResultSet resultSet = statement.executeQuery(query)) {
                        // Iterate over the results and add them to the PDF
                        int yCoordinate = 680; // Start y-coordinate for the text
                        while (resultSet.next()) {
                            String personName = resultSet.getString("name");
                            String email = resultSet.getString("email");

                            // Write person details to the PDF
                            contentStream.beginText();
                            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                            contentStream.newLineAtOffset(100, yCoordinate);
                            contentStream.showText("Person: " + personName);
                            contentStream.newLine();
                            contentStream.showText("Email: " + email);
                            contentStream.newLine();
                            contentStream.endText();

                            yCoordinate -= 40; // Move to the next line
                        }
                    }
                }
            }

            document.save(outputStream);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
        return outputStream;
    }

}
