/*
 * Copyright (c) 2017. Cristian Do Carmo Rodriguez
 */


package color.dev.com.red;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;

public class FaiticDeprecatedNuevo implements Serializable {


    private static final String urlMain = "http://faitic.uvigo.es/index.php/es/";
    private static final String urlSubjects = "http://faitic.uvigo.es/index.php/es/materias";
    private static CookieManager cookieManager;
    public static Logger logger;

    private static boolean cCancelDownload = false;
    private static Semaphore sCancelDownload = new Semaphore(1);

    public FaiticDeprecatedNuevo(boolean verbose) {
        toDoAtStartup(verbose);
    }

    private static void toDoAtStartup(boolean verbose) {

        startCookieSession();
        logger = new Logger(verbose);

    }

    protected static boolean getCancelDownload() {

        try {

            sCancelDownload.acquire();
            boolean out = cCancelDownload;
            sCancelDownload.release();

            return out;

        } catch (Exception ex) {

            // Weird. Stop the download just in case

            ex.printStackTrace();
            return true;

        }

    }

    protected static void setCancelDownload(boolean value) {

        try {

            sCancelDownload.acquire();
            cCancelDownload = value;
            sCancelDownload.release();

        } catch (Exception ex) {

            ex.printStackTrace();

        }

    }

    private static void startCookieSession() {

        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        cookieManager.getCookieStore();//.removeAll();

        CookieHandler.setDefault(cookieManager);


    }


    public static String lastRequestedURL = "";

    public static String requestDocument(String strurl, String post) throws Exception {

        CookieHandler.setDefault(cookieManager);

        lastRequestedURL = strurl;

        logger.log(Logger.INFO, "Requesting URL: " + strurl);
        logger.log(Logger.INFO, "Post data: " + post);

        logger.log(Logger.INFO, "--- Creating connection ---");

        URL url = new URL(adaptURL(lastRequestedURL, strurl));

        List<HttpCookie> cookiesAssoc = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr = "";

        for (HttpCookie cookieAssoc : cookiesAssoc) {

            cookiesAssocStr += (cookiesAssocStr.length() > 0 ? "; " : "") + cookieAssoc.getName() + "=" + cookieAssoc.getValue();

        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        //NUEVO
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        Log.v("1", "3");
        // Time out settings
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);

        connection.setRequestProperty("Accept-Encoding", "gzip");

        if (post.length() > 0) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", "" + post.length());
        }

        if (cookiesAssocStr.length() > 0) {
            connection.setRequestProperty("Cookie", cookiesAssocStr);
            logger.log(Logger.INFO, "Cookies: " + cookiesAssocStr);
        }

        DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
        writer.write(post.getBytes(StandardCharsets.UTF_8));

        logger.log(Logger.INFO, "--- Petition sent. Reading ---");

        StringBuffer output = new StringBuffer();
        InputStream reader;

        if (connection.getContentEncoding().equals("gzip")) {

            reader = new GZIPInputStream(connection.getInputStream());
            logger.log(Logger.INFO, " + GZIP ENCODED");

        } else {

            reader = connection.getInputStream();

        }

        byte[] temp = new byte[1000];
        int read = reader.read(temp);

        int counter = 0;

        while (read != -1) {
            output.append(new String(temp, 0, read, StandardCharsets.UTF_8));
            read = reader.read(temp);
            counter += read;

        }

        reader.close();

        int status = connection.getResponseCode();

        String headerName;

        for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {

            if (headerName.toLowerCase().equals("set-cookie")) {

                String cookiesToSet = connection.getHeaderField(i);

                for (String cookieToSet : cookiesToSet.split(";")) {

                    String[] cookieParameters = cookieToSet.split("=");


                    if (cookieParameters[0].contains("path") || cookieParameters[0].contains("expire")) {
                        Log.v("GALLETA NO CARGADA", cookieParameters[1]);
                    } else {

                        HttpCookie galleta = new HttpCookie(cookieParameters[0].trim(), cookieParameters[1].trim());
                        galleta.setPath("/");

                        cookieManager.getCookieStore().add((url).toURI(), galleta);

                        logger.log(Logger.INFO, " + Adding cookie \"" + cookieToSet + "\" to uri \"" + (url).toURI().toString() + "\".");

                    }
                }


            }

        }

        List<HttpCookie> cookiesAssoc2 = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr2 = "";

        for (HttpCookie cookieAssoc2 : cookiesAssoc2) {

            cookiesAssocStr2 += (cookiesAssocStr2.length() > 0 ? "; " : "") + cookieAssoc2.getName() + "=" + cookieAssoc2.getValue();

        }

        Log.v("COOKIES DE DESPUES", "==>" + cookiesAssocStr2);


        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {


            logger.log(Logger.INFO, "--- Redirected ---");

            return requestDocument(adaptURL(lastRequestedURL, connection.getHeaderField("Location")), "");


        } else {

            logger.log(Logger.INFO, "--- Request finished ---\n");

            return output.toString();

        }

    }

    public static void downloadFile(String strurl, String post, String filename) throws Exception {

        if (getCancelDownload()) return;    // Download cancelled, don't dare to continue

        System.out.println(" -- Downloading file from \"" + strurl + "\" and saving to \"" + filename + "\"...");

        lastRequestedURL = strurl;

        logger.log(Logger.INFO, "Requesting URL: " + strurl);
        logger.log(Logger.INFO, "Post data: " + post);

        logger.log(Logger.INFO, "--- Creating connection ---");

        URL url = new URL(strurl);

        List<HttpCookie> cookiesAssoc = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr = "";

        for (HttpCookie cookieAssoc : cookiesAssoc) {

            cookiesAssocStr += (cookiesAssocStr.length() > 0 ? "; " : "") + cookieAssoc.getName() + "=" + cookieAssoc.getValue();

        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//NUEVO
        //CookieHandler.setDefault(cookieManager);

        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        Log.v("1", "3");
        // Time out settings
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);

        if (post.length() > 0) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", "" + post.length());
        }

        if (cookiesAssocStr.length() > 0) {
            connection.setRequestProperty("Cookie", cookiesAssocStr);
            logger.log(Logger.INFO, "Cookies: " + cookiesAssocStr);
        }

        DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
        writer.write(post.getBytes(StandardCharsets.UTF_8));

        logger.log(Logger.INFO, "--- Petition sent. Reading ---");

        // Check cookies and if the document redirects

        int status = connection.getResponseCode();

        String headerName;

        for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {

            if (headerName.toLowerCase().equals("set-cookie")) {

                String cookiesToSet = connection.getHeaderField(i);

                for (String cookieToSet : cookiesToSet.split(";")) {

                    String[] cookieParameters = cookieToSet.split("=");

                    //CookieHandler.setDefault(null);


                    if (cookieParameters[0].contains("path") || cookieParameters[0].contains("expire")) {
                        Log.v("GALLETA NO CARGADA", cookieParameters[1]);
                    } else {

                        HttpCookie galleta = new HttpCookie(cookieParameters[0].trim(), cookieParameters[1].trim());
                        galleta.setPath("/");

                        cookieManager.getCookieStore().add(url.toURI(), galleta);

                        logger.log(Logger.INFO, " + Adding cookie \"" + cookieToSet + "\" to uri \"" + url.toURI().toString() + "\".");

                    }
                }


            }

        }

        List<HttpCookie> cookiesAssoc2 = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr2 = "";

        for (HttpCookie cookieAssoc2 : cookiesAssoc2) {

            cookiesAssocStr2 += (cookiesAssocStr2.length() > 0 ? "; " : "") + cookieAssoc2.getName() + "=" + cookieAssoc2.getValue();

        }

        Log.v("COOKIES DE DESPUES", "==>" + cookiesAssocStr2);

        // Does the document redirect?

        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {


            logger.log(Logger.INFO, "--- Redirected ---");

            downloadFile(adaptURL(lastRequestedURL, connection.getHeaderField("Location")), "", filename);

            return;

        }

        // OK, the document doesn't redirect. Download it

        InputStream reader;    // Response document

        reader = connection.getInputStream();

        // Let's write the document

        FileOutputStream filewriter;

        int tempfilenumber = 1;
        String tempfilename = filename + ".tmp" + tempfilenumber;

        while (new File(tempfilename).exists()) {

            // Iterates until the file doesn't exist

            tempfilename = filename + ".tmp" + (++tempfilenumber);

        }

        logger.log(Logger.INFO, " + Saving temp as: " + tempfilename);

        filewriter = new FileOutputStream(tempfilename);

        byte[] temp;
        int read;

        try {

            temp = new byte[1000];
            read = reader.read(temp);

            while (read != -1 && !getCancelDownload()) {
                filewriter.write(temp, 0, read);

                read = reader.read(temp);

            }

            // Close the writers

            filewriter.close();
            reader.close();

            if (!getCancelDownload()) {

                // Success. Substitute the file

                logger.log(Logger.INFO, " + Renaming temp to: " + filename);

                File oldfile = new File(filename);
                File tempfile = new File(tempfilename);

                boolean deletingsuccess = true;

                if (oldfile.exists()) {
                    if (!oldfile.isDirectory()) {

                        deletingsuccess = oldfile.delete();

                    } else {

                        deletingsuccess = false;

                    }
                }

                if (deletingsuccess) {

                    // Correctly deleted

                    tempfile.renameTo(oldfile);

                    System.out.println("Success.");


                }

            } else {

                logger.log(Logger.ERROR, "--- Download cancelled ---\n");

            }

        } catch (Exception ex) {

            ex.printStackTrace();

            try {
                filewriter.close();
            } catch (Exception ex2) {
                ex2.printStackTrace();
            }
            try {
                reader.close();
            } catch (Exception ex2) {
                ex2.printStackTrace();
            }

        }

        logger.log(Logger.INFO, "--- Request finished ---\n");


        return;

    }

    public static String getRedirectedURL(String strurl, String post) throws Exception {

        lastRequestedURL = strurl;

        logger.log(Logger.INFO, "Requesting URL: " + strurl);
        logger.log(Logger.INFO, "Post data: " + post);

        logger.log(Logger.INFO, "--- Creating connection ---");

        URL url = new URL(strurl);

        List<HttpCookie> cookiesAssoc = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr = "";

        for (HttpCookie cookieAssoc : cookiesAssoc) {

            cookiesAssocStr += (cookiesAssocStr.length() > 0 ? "; " : "") + cookieAssoc.getName() + "=" + cookieAssoc.getValue();

        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//NUEVO
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        Log.v("1", "3");
        // Time out settings
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);

        connection.setRequestProperty("Accept-Encoding", "gzip");

        if (post.length() > 0) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", "" + post.length());
        }

        if (cookiesAssocStr.length() > 0) {
            connection.setRequestProperty("Cookie", cookiesAssocStr);
            logger.log(Logger.INFO, "Cookies: " + cookiesAssocStr);
        }

        DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
        writer.write(post.getBytes(StandardCharsets.UTF_8));

        logger.log(Logger.INFO, "--- Petition sent. Waiting for redirecting info ---");

        int status = connection.getResponseCode();

        String headerName;

        // Getting cookies

        for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {

            if (headerName.toLowerCase().equals("set-cookie")) {

                String cookiesToSet = connection.getHeaderField(i);

                for (String cookieToSet : cookiesToSet.split(";")) {

                    String[] cookieParameters = cookieToSet.split("=");

                    //CookieHandler.setDefault(null);
                    //CookieHandler.setDefault(cookieManager);


                    if (cookieParameters[0].contains("path") || cookieParameters[0].contains("expire")) {
                        Log.v("GALLETA NO CARGADA", cookieParameters[1]);
                    } else {

                        HttpCookie galleta = new HttpCookie(cookieParameters[0].trim(), cookieParameters[1].trim());
                        galleta.setPath("/");

                        cookieManager.getCookieStore().add(url.toURI(), galleta);

                        logger.log(Logger.INFO, " + Adding cookie \"" + cookieToSet + "\" to uri \"" + url.toURI().toString() + "\".");

                    }
                }


            }

        }

        List<HttpCookie> cookiesAssoc2 = cookieManager.getCookieStore().get(url.toURI());
        String cookiesAssocStr2 = "";

        for (HttpCookie cookieAssoc2 : cookiesAssoc2) {

            cookiesAssocStr2 += (cookiesAssocStr2.length() > 0 ? "; " : "") + cookieAssoc2.getName() + "=" + cookieAssoc2.getValue();

        }

        Log.v("COOKIES DE DESPUES", "==>" + cookiesAssocStr2);


        // Return status, there will be the redirection

        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {


            logger.log(Logger.INFO, "--- Redirected. ---");

            String redURL = adaptURL(lastRequestedURL, connection.getHeaderField("Location"));

            logger.log(Logger.INFO, "URL: " + redURL);
            return redURL;


        } else {

            logger.log(Logger.INFO, "--- Request finished. Not redirected ---\n");

            return null;

        }

    }

    public static String generatePostLogin(String username, String password) throws Exception {

        StringBuffer output = new StringBuffer();

        String documentMain = requestDocument(urlMain, "");

        int formStart = documentMain.toLowerCase().indexOf("<form action=\"/index.php/es/\" method=\"post\" id=\"login-form\"");

        int formEnd = documentMain.toLowerCase().indexOf("</form>", formStart);

        // Form detected

        if (formStart >= 0 && formEnd >= 0) {

            int currentpos = documentMain.toLowerCase().indexOf("<input", formStart);

            while (currentpos >= formStart && currentpos < formEnd) {

                String type = null, name = null, value = null;

                int closer = documentMain.toLowerCase().indexOf(">", currentpos);

                String[] sentence = documentMain.substring(currentpos, closer).split(" ");    // The input divided by the spaces

                for (String sentencePart : sentence) {    // Read the parts of the input

                    String partname = sentencePart.substring(0, sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") : 0);

                    String partvalue = sentencePart.substring(sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") + 1 : 0, sentencePart.length());


                    switch (partname.toLowerCase()) {

                        case "type":
                            type = partvalue.replace("\"", "");
                            break;
                        case "name":
                            name = partvalue.replace("\"", "");
                            break;
                        case "value":
                            value = partvalue.replace("\"", "");
                            break;

                        default:
                            ;

                    }

                }

                if (type != null && name != null && value != null)
                    if (!type.toLowerCase().contains("checkbox")) { // To be sent

                        if (output.length() > 0) output.append("&");

                        output.append(name + "=" + URLEncoder.encode(value, "UTF-8"));

                    }

                // Prepare for next while loop
                currentpos = documentMain.toLowerCase().indexOf("<input", currentpos + 1);

            }


        }

        if (output.length() > 0) output.append("&");
        output.append("username=" + URLEncoder.encode(username, "UTF-8") + "&password=" + URLEncoder.encode(password, "UTF-8"));

        return output.toString();

    }

    public static String faiticLogin(String username, String password) throws Exception {

        String responseToLogin = requestDocument(urlMain, generatePostLogin(username, password));

        int errorToLoginIndex = responseToLogin.indexOf("<dd class=\"error message\">");

        // If there was an error
        if (errorToLoginIndex >= 0) {

            int firstLiError = responseToLogin.indexOf("<li>", errorToLoginIndex);
            int lastLiError = responseToLogin.indexOf("</li>", errorToLoginIndex);

            if (firstLiError > 0 && lastLiError > firstLiError) {

                logger.log(Logger.ERROR, " -- Error: " + responseToLogin.substring(firstLiError + 4, lastLiError) + " -- ");
                return null;

            }

        }

        // No error, go to the document we want (Languages change the destination)

        //return responseToLogin;
        return requestDocument(urlSubjects, "");


    }


    public static String faiticLogout(String documentMain) throws Exception {

        StringBuffer output = new StringBuffer();

        int formStart = documentMain.toLowerCase().indexOf("<form action=\"/index.php/es/materias\" method=\"post\" id=\"login-form\"");

        int formEnd = documentMain.toLowerCase().indexOf("</form>", formStart);

        // Form detected

        if (formStart >= 0 && formEnd >= 0) {

            int currentpos = documentMain.toLowerCase().indexOf("<input", formStart);

            while (currentpos >= formStart && currentpos < formEnd) {

                String type = null, name = null, value = null;

                int closer = documentMain.toLowerCase().indexOf(">", currentpos);

                String[] sentence = documentMain.substring(currentpos, closer).split(" ");    // The input divided by the spaces

                for (String sentencePart : sentence) {    // Read the parts of the input

                    String partname = sentencePart.substring(0, sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") : 0);

                    String partvalue = sentencePart.substring(sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") + 1 : 0, sentencePart.length());


                    switch (partname.toLowerCase()) {

                        case "type":
                            type = partvalue.replace("\"", "");
                            break;
                        case "name":
                            name = partvalue.replace("\"", "");
                            break;
                        case "value":
                            value = partvalue.replace("\"", "");
                            break;

                        default:
                            ;

                    }

                }

                if (type != null && name != null && value != null)
                    if (!type.toLowerCase().contains("checkbox")) { // To be sent

                        if (output.length() > 0) output.append("&");

                        output.append(name + "=" + URLEncoder.encode(value, "UTF-8"));

                    }

                // Prepare for next while loop
                currentpos = documentMain.toLowerCase().indexOf("<input", currentpos + 1);

            }


        }

        return requestDocument(urlSubjects, output.toString());

    }


    public static ArrayList<Subject> faiticSubjects(String documentToCheck) {    // 0 url 1 name

        ArrayList<Subject> subjectList = new ArrayList<Subject>();

        // Login was unsuccessful
        if (documentToCheck == null) return subjectList;

        // Login successful:

        int subjectIndex = documentToCheck.indexOf("<span class=\"asignatura\"");

        while (subjectIndex >= 0) {

            // Check subjects one by one

            int hrefIndex = documentToCheck.indexOf("<a href=\"", subjectIndex);
            int hrefURLCloserIndex = documentToCheck.indexOf("\"", hrefIndex + "<a href=\"".length());

            int hrefFirstTagCloserIndex = documentToCheck.indexOf(">", hrefURLCloserIndex);
            int hrefSecondTagOpenerIndex = documentToCheck.indexOf("<", hrefFirstTagCloserIndex);


            String subjectURL = documentToCheck.substring(hrefIndex + "<a href=\"".length(), hrefURLCloserIndex);
            String subjectName = documentToCheck.substring(hrefFirstTagCloserIndex + 1, hrefSecondTagOpenerIndex).trim();

            subjectList.add(new Subject(subjectURL, subjectName));

            subjectIndex = documentToCheck.indexOf("<span class=\"asignatura\"", subjectIndex + 1);

        }

        return subjectList;

    }

    public static DocumentFromURL goToSubject(String url) throws Exception {    // 0 is the url and 1 is the document itself

        //CookieHandler.setDefault(cookieManager);

        String documentMain = requestDocument(url, "");

        StringBuffer output = new StringBuffer();

        int formStart = documentMain.toLowerCase().indexOf("<form name='frm'");

        int formEnd = documentMain.toLowerCase().indexOf("</form>", formStart);

        int actionStart = documentMain.indexOf("action='", formStart);
        int actionEnd = documentMain.indexOf("'", actionStart + "action='".length());

        String urlForAction = documentMain.substring(actionStart + "action='".length(), actionEnd);

        // Form detected
        Log.v("GOTOSUBJECT", "x2" + ((formStart >= 0 && formEnd >= 0) ? "true" : "false"));


        if (formStart >= 0 && formEnd >= 0) {

            int currentpos = documentMain.toLowerCase().indexOf("<input", formStart);

            while (currentpos >= formStart && currentpos < formEnd) {

                String type = null, name = null, value = null;

                int closer = documentMain.toLowerCase().indexOf(">", currentpos);

                String[] sentence = documentMain.substring(currentpos, closer).split(" ");    // The input divided by the spaces

                for (String sentencePart : sentence) {    // Read the parts of the input

                    String partname = sentencePart.substring(0, sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") : 0);

                    String partvalue = sentencePart.substring(sentencePart.indexOf("=") >= 0 ? sentencePart.indexOf("=") + 1 : 0, sentencePart.length());


                    switch (partname.toLowerCase()) {

                        case "type":
                            type = partvalue.replace("'", "");
                            break;
                        case "name":
                            name = partvalue.replace("'", "");
                            break;
                        case "value":
                            value = partvalue.replace("'", "");
                            break;

                        default:
                            ;

                    }

                }

                if (type != null && name != null && value != null)
                    if (!type.toLowerCase().contains("checkbox")) { // To be sent

                        if (output.length() > 0) output.append("&");

                        output.append(name + "=" + URLEncoder.encode(value, "UTF-8"));

                    }

                // Prepare for next while loop
                currentpos = documentMain.toLowerCase().indexOf("<input", currentpos + 1);

            }


        }

        return new DocumentFromURL(urlForAction, requestDocument(urlForAction, output.toString()));


    }

    public static final int CLAROLINE = 0;
    public static final int MOODLE = 1;
    public static final int MOODLE2 = 2;
    public static final int UNKNOWN = 99;

    public static int subjectPlatformType(String url) {

        if (url.toLowerCase().contains("/claroline/")) {
            return CLAROLINE;
        } else if (url.toLowerCase().contains("/moodle") && !url.toLowerCase().contains("/moodle2_")) {
            return MOODLE;
        } else if (url.toLowerCase().contains("/moodle2_")) {
            return MOODLE2;
        } else {
            return UNKNOWN;
        }

    }

    public static void logoutSubject(String platformURL, String platformDocument, int platformType) throws Exception {

        if (platformType == CLAROLINE) {

            String logoutURL = platformURL.substring(0, platformURL.lastIndexOf("?") >= 0 ? platformURL.lastIndexOf("?") : platformURL.length()) + "?logout=true";

            requestDocument(logoutURL, "");

        } else if (platformType == MOODLE || platformType == MOODLE2) {

            // More complicated :( pay attention because this is about to start...

            int endOfURLShouldStartWith = platformURL.indexOf("/", platformURL.indexOf("/moodle") + 1);

            if (endOfURLShouldStartWith >= 0) {

                String logoutURLShouldStartWith = platformURL.substring(0, endOfURLShouldStartWith) + "/login/logout.php";
                // This is the url that should appear on the document, but with all the parameters given as GET

                // Let's look for this entry

                int hereIsTheLogoutURL = platformDocument.indexOf(logoutURLShouldStartWith);

                int hereEndsTheLogoutURL = platformDocument.indexOf("\"", hereIsTheLogoutURL);

                //System.out.println("\n\n" + logoutURLShouldStartWith + "\n\n");

                if (hereIsTheLogoutURL >= 0 && hereEndsTheLogoutURL > hereIsTheLogoutURL) {

                    // Gotcha!

                    requestDocument(platformDocument.substring(hereIsTheLogoutURL, hereEndsTheLogoutURL), "");

                }

            }

        }

    }

    public static ArrayList<FileFromURL> listDocumentsClaroline(String platformURL) throws Exception {
        
		/*
		 * 0 -> Path (incl. filename)
		 * 1 -> URL to file
		 */

        ArrayList<FileFromURL> list = new ArrayList<FileFromURL>();

        int untilWhenUrlToUse = platformURL.indexOf("/", platformURL.indexOf("/claroline") + 1);

        if (untilWhenUrlToUse >= 0) {

            String urlBase = platformURL.substring(0, untilWhenUrlToUse);
            String urlToUse = urlBase + "/document/document.php";
            listDocumentsClarolineInternal(urlToUse, list, urlBase);    // Recursive

        }

        cleanArtifacts(list);
        deleteRepeatedFiles(list);

        return list;

    }

    private static void listDocumentsClarolineInternal(String urlToAnalyse, ArrayList<FileFromURL> list, String urlBase) throws Exception {

        String document;

        try {

            document = requestDocument(urlToAnalyse, "");

        } catch (Exception ex) {

            return;

        }

        if (!urlToAnalyse.equals(lastRequestedURL)) return;        // If the page redirected us

        // Check for documents...

        int dirStart = document.indexOf("<a class=\" item");

        int dirEnd = document.lastIndexOf("End of Claroline Body");

        if (dirStart >= 0 && dirEnd > dirStart) {

            String documentToAnalyse = document.substring(dirStart, dirEnd);

            // First check for files

            int ocurrence = documentToAnalyse.indexOf("goto/index.php");

            while (ocurrence >= 0) {

                int endOfOcurrence = documentToAnalyse.indexOf("\"", ocurrence + 1);

                if (endOfOcurrence > ocurrence) {

                    String urlGot = urlBase + "/document/" + documentToAnalyse.substring(ocurrence, endOfOcurrence).replace("&amp;", "&").replace(" ", "%20");

                    String pathForFile = urlGot.substring((urlBase + "/document/goto/index.php/").length(), urlGot.lastIndexOf("?") >= 0 ? urlGot.lastIndexOf("?") : urlGot.length());

                    list.add(new FileFromURL(urlGot, URLDecoder.decode("/" + pathForFile, "iso-8859-1")));

                }

                ocurrence = documentToAnalyse.indexOf("goto/index.php", ocurrence + 1);

            }


            // Now for directories

            ocurrence = documentToAnalyse.indexOf("/document/document.php?cmd=exChDir");

            while (ocurrence >= 0) {

                int endOfOcurrence = documentToAnalyse.indexOf("\"", ocurrence + 1);

                if (endOfOcurrence > ocurrence) {

                    String urlGot = urlBase + documentToAnalyse.substring(ocurrence, endOfOcurrence).replace("&amp;", "&").replace(" ", "%20");

                    listDocumentsClarolineInternal(urlGot, list, urlBase);

                }

                ocurrence = documentToAnalyse.indexOf("/document/document.php?cmd=exChDir", ocurrence + 1);

            }

        }


    }


    public static ArrayList<FileFromURL> listDocumentsMoodle(String platformURL) throws Exception {
		
		/*
		 * 0 -> Path (incl. filename)
		 * 1 -> URL to file
		 */

        ArrayList<FileFromURL> list = new ArrayList<FileFromURL>();

        int untilWhenUrlToUse = platformURL.indexOf("/", platformURL.indexOf("/moodle") + 1);

        if (untilWhenUrlToUse >= 0) {

            String urlBase = platformURL.substring(0, untilWhenUrlToUse);
            String urlGetMethod = platformURL.indexOf("?") >= 0 ? platformURL.substring(platformURL.indexOf("?") + 1, platformURL.length()) : "";
            String urlForResources = urlBase + "/mod/resource/index.php" + (urlGetMethod.length() > 0 ? "?" + urlGetMethod : "");

            listDocumentsMoodleInternal(urlForResources, list, urlBase);

        }

        cleanArtifacts(list);
        deleteRepeatedFiles(list);

        return list;

    }


    private static void listDocumentsMoodleInternal(String urlToUse, ArrayList<FileFromURL> list, String urlBase) throws Exception {

        //System.out.println("---Accessed---");

        String resourcePage;

        try {

            resourcePage = requestDocument(urlToUse, "");

        } catch (Exception ex) {

            return;

        }

        if (!urlToUse.equals(lastRequestedURL)) return;        // If the page redirected us

        // The list of files from this resource

        int bodyStart = resourcePage.indexOf("<!-- END OF HEADER -->");

        int bodyEnd = resourcePage.indexOf("<!-- START OF FOOTER -->", bodyStart);

        if (bodyStart >= 0 && bodyEnd > bodyStart) {

            String whereToSearch = resourcePage.substring(bodyStart, bodyEnd);

            int URLStart = whereToSearch.indexOf(urlBase + "/file.php/");
            int URLEnd = whereToSearch.indexOf("\"", URLStart);

            if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                URLEnd = whereToSearch.indexOf("\'", URLStart);

            while (URLStart >= 0 && URLStart < URLEnd) {

                String urlToFile = whereToSearch.substring(URLStart, URLEnd);
                urlToFile = urlToFile.replace("&amp;", "&");

                int filePathStart = urlToFile.indexOf("/", (urlBase + "/file.php/").length() + 1);

                String filePath = urlToFile.substring(filePathStart, urlToFile.length());

                list.add(new FileFromURL(urlToFile, URLDecoder.decode(filePath, "iso-8859-1")));    // Added to list

                // For next loop

                URLStart = whereToSearch.indexOf(urlBase + "/file.php/", URLEnd);
                URLEnd = whereToSearch.indexOf("\"", URLStart);

                if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                    URLEnd = whereToSearch.indexOf("\'", URLStart);

            }

            // Then directories

            URLStart = whereToSearch.indexOf("view.php?");
            URLEnd = whereToSearch.indexOf("\"", URLStart);

            if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                URLEnd = whereToSearch.indexOf("\'", URLStart);

            while (URLStart >= 0 && URLStart < URLEnd) {

                String urlList = urlBase + "/mod/resource/" + whereToSearch.substring(URLStart, URLEnd);
                urlList = urlList.replace("&amp;", "&").replace(" ", "%20");

                // We have got the url, but we don't know if it's a folder or not, let's check it

                try {

                    String realurl = getRedirectedURL(urlList, "");

                    if (realurl == null) {

                        // Folder, recursive search

                        listDocumentsMoodleInternal(urlList, list, urlBase);

                    } else {

                        // Document, let's get the real name

                        String realname = "undefined";

                        int filePathStart = realurl.indexOf("/", (urlBase + "/file.php/").length() + 1);

                        if (filePathStart >= 0) {

                            String filePath = realurl.substring(filePathStart, realurl.length());

                            list.add(new FileFromURL(realurl, URLDecoder.decode(filePath, "iso-8859-1")));    // Added to list

                        }


                    }


                } catch (Exception ex) {

                    ex.printStackTrace();

                }


                // For next loop

                URLStart = whereToSearch.indexOf("view.php?", URLEnd);
                URLEnd = whereToSearch.indexOf("\"", URLStart);

                if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                    URLEnd = whereToSearch.indexOf("\'", URLStart);

            }

        }


    }


    public static ArrayList<FileFromURL> listDocumentsMoodle2(String platformURL) throws Exception {
		
		/*
		 * 0 -> Path (incl. filename)
		 * 1 -> URL to file
		 */

        ArrayList<FileFromURL> list = new ArrayList<FileFromURL>();

        int untilWhenUrlToUse = platformURL.indexOf("/", platformURL.indexOf("/moodle") + 1);

        if (untilWhenUrlToUse >= 0) {

            String urlBase = platformURL.substring(0, untilWhenUrlToUse);
            String urlGetMethod = platformURL.indexOf("?") >= 0 ? platformURL.substring(platformURL.indexOf("?") + 1, platformURL.length()) : "";
            String urlForResources = urlBase + "/mod/resource/index.php" + (urlGetMethod.length() > 0 ? "?" + urlGetMethod : "");

            listDocumentsMoodle2Internal(urlForResources, list, urlBase, "");

        }

        cleanArtifacts(list);
        deleteRepeatedFiles(list);

        return list;

    }


    private static void listDocumentsMoodle2Internal(String urlToUse, ArrayList<FileFromURL> list, String urlBase, String folder) throws Exception {

        //System.out.println("---Accessed---");

        String resourcePage;

        try {

            resourcePage = requestDocument(urlToUse, "");

        } catch (Exception ex) {

            return;

        }

        if (!urlToUse.equals(lastRequestedURL)) return;        // If the page redirected us

        // The list of files from this resource

        int bodyStart = resourcePage.indexOf("<div id=\"page-content\"");

        int bodyEnd = resourcePage.indexOf("</section>", bodyStart);

        if (bodyStart >= 0 && bodyEnd > bodyStart) {

            String whereToSearch = resourcePage.substring(bodyStart, bodyEnd);

            int URLStart = whereToSearch.indexOf("view.php?");
            int URLEnd = whereToSearch.indexOf("\"", URLStart);

            if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                URLEnd = whereToSearch.indexOf("\'", URLStart);

            while (URLStart >= 0 && URLStart < URLEnd) {

                String urlList = urlBase + "/mod/resource/" + whereToSearch.substring(URLStart, URLEnd);
                urlList = urlList.replace("&amp;", "&");

                // We have got the url, but we don't know if it's a folder or not, let's check it

                int indeximg = whereToSearch.indexOf("<img src=", URLEnd);
                int endofimg = whereToSearch.indexOf(">", indeximg);

                int endofa = whereToSearch.indexOf("<", endofimg);

                int folderindex = whereToSearch.indexOf("folder-24", indeximg);

                String filename = endofimg >= 0 && endofa > endofimg ? whereToSearch.substring(endofimg + 1, endofa).trim() : "undefined";

                if (folderindex >= 0 && folderindex < endofimg) {

                    // Folder, recursive search

                    listDocumentsMoodle2Internal(urlList, list, urlBase, folder + "/" + filename);

                } else {

                    // Document, let's get the real name

                    try {

                        String realurl = getRedirectedURL(urlList, "");
                        String realname = filename;    // By now

                        if (realurl != null) {

                            // Redirected, get the real name

                            int questionMarkIndex = realurl.indexOf("?");
                            int lastDivider = realurl.substring(0, questionMarkIndex >= 0 ? questionMarkIndex : realurl.length()).lastIndexOf("/");    // No error because it starts at 0

                            if (lastDivider >= 0) {

                                // Got a name

                                realname = URLDecoder.decode(realurl.substring(lastDivider + 1, questionMarkIndex >= 0 ? questionMarkIndex : realurl.length()), "UTF-8");

                            }


                        }

                        list.add(new FileFromURL(urlList, folder + "/" + realname));

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }

                // For next loop

                URLStart = whereToSearch.indexOf("view.php?", URLEnd);
                URLEnd = whereToSearch.indexOf("\"", URLStart);

                if (whereToSearch.indexOf("\'", URLStart) < URLEnd && whereToSearch.indexOf("\'", URLStart) >= 0)
                    URLEnd = whereToSearch.indexOf("\'", URLStart);

            }

        }

    }

    protected static void deleteRepeatedFiles(ArrayList<FileFromURL> list) {    // Deletes files with same url

        // Make a copy of list

        int pos = 0;

        while (pos < list.size()) {    // From 0 to size

            FileFromURL element = list.get(pos);    // To compare

            int i = pos + 1;

            while (i < list.size()) {    // From pos+1 to size

                // 1 is url
                if (element.getURL().equals(list.get(i).getURL())) {

                    list.remove(i);    // Delete element
                    i--;            // The i index must be reduced

                    //out.set(i, new String[]{"Repeated:" + out.get(i)[0],out.get(i)[1]});

                }

                i++;
            }

            pos++;

        }

    }

    protected static void cleanArtifacts(ArrayList<FileFromURL> list) {

        for (int i = 0; i < list.size(); i++) {

            FileFromURL element = list.get(i);

            // First for the name
            String name = element.getFileDestination().trim();    // Trim path

            int until = name.indexOf("<");
            until = name.indexOf(">") >= 0 && name.indexOf(">") < until ? name.indexOf(">") : until;

            if (until >= 0) name = name.substring(0, until);    // Delete unwanted exceeded code
            name = name.replaceAll("[*?\"<>|]", "_");    // Correct special characters

            if (name.length() <= 0) name = "undefined"; // Just in case

            // Second for the url

            String url = element.getURL().trim();    // Trim url

            until = url.indexOf("<") <= url.indexOf(">") ? url.indexOf("<") : url.indexOf(">");

            if (until >= 0) url = url.substring(0, until);        // Delete unwanted exceeded code

            list.set(i, new FileFromURL(url, name));

        }

    }

    protected static String adaptURL(String prevURL, String url) {

        Log.v("PENa=>>", url.indexOf("/") + "");
        if (url.indexOf("/") == 0 && prevURL != null) {

            // It is not a complete url, get the previous server

            Log.v("PENa", "1");

            int doubleslashpos = prevURL.indexOf("//");

            if (doubleslashpos >= 0) {

                Log.v("PENa", "2");

                int rootslash = prevURL.indexOf("/", doubleslashpos + 2);

                Log.v("PENa", "3");

                if (rootslash >= 0) {

                    // The base url
                    String baseURL = prevURL.substring(0, rootslash);

                    Log.v("PENa", baseURL);

                    // The url and the relative url
                    return baseURL + url;

                }
            }

        }

        Log.v("PENA", "GAYA>" + url);
        // if nothing is reached
        return url;
    }


}
