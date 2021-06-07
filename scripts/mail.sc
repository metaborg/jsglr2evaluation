import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $ivy.`com.sendgrid:sendgrid-java:4.7.2`, com.sendgrid._, com.sendgrid.helpers.mail._, com.sendgrid.helpers.mail.objects._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

import java.io.File
import java.nio.file.{Files, Paths}

println("Sending archive by email...")

val sendgridToken = sys.env.get("SENDGRID_TOKEN").get
val email = sys.env.get("EMAIL").get

val sendgrid = new SendGrid(sendgridToken)

val from = new Email("j.denkers@tudelft.nl")
val subject = "JSGLR2 Evaluation Results"
val to = new Email(email)
val content = new Content("text/plain", " ")
val mail = new Mail()

val personalization = new Personalization()

email.split(",").map(new Email(_)).foreach(personalization.addTo)

mail.addPersonalization(personalization)
mail.setFrom(from)
mail.setSubject(subject)
mail.addContent(content)

val bytes = Files.readAllBytes(Paths.get((suite.dir / "archive.tar.gz").toString))
val b64 = new sun.misc.BASE64Encoder().encode(bytes)

val attachments = new Attachments();
attachments.setContent(b64);
attachments.setType("application/gzip");
attachments.setFilename("archive.tar.gz");
attachments.setDisposition("attachment");
attachments.setContentId("archive");

mail.addAttachments(attachments);

val request = new Request()

request.setMethod(Method.POST)
request.setEndpoint("mail/send")
request.setBody(mail.build())

val response = sendgrid.api(request)

println("SendGrid response: " + response.getStatusCode + " " + response.getBody + " " + response.getHeaders)