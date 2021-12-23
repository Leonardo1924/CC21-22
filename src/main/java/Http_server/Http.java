package Http_server;
import FFSync.FTrapid;
import org.javatuples.Quartet;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Http implements Runnable{

    private final static int PORT = 8080;
    private FTrapid ftr;
    private ServerSocket serverSocket;


    public Http(FTrapid ftr){
        this.ftr = ftr;
    }


    public void close() throws IOException {

        if(!this.serverSocket.isClosed())
            this.serverSocket.close();
    }

    public void run() {

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            this.serverSocket = serverSocket;
            while (true) {
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                } catch (IOException e) {
                    if(this.serverSocket.isClosed()){
                        System.out.println("Server is closed");
                        return;
                    }
                }
            }
        } catch (IOException e){

                System.out.println("error: nao foi possível iniciar o servidor http");
        }
    }

    private void handleClient(Socket client) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));

        StringBuilder requestBuilder = new StringBuilder();
        String line;

        if((line = br.readLine()) != null) {

            requestBuilder.append(line).append("\r\n");
            while (!(line = br.readLine()).isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
        }
        String request = requestBuilder.toString();
        String[] requestsLines = request.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");

        String method;
        String path = null;
        if(requestLine.length >= 1)
            method = requestLine[0];
        if(requestLine.length >= 2)
            path = requestLine[1];
        //String version = requestLine[2];
        //String host = requestsLines[1].split(" ")[1];

        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) {
            String header = requestsLines[h];
            headers.add(header);
        }

        //String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s",
        //        client.toString(), method, path, version, host, headers.toString());
        //System.out.println(accessLog);

        if(path != null) {
            Path filePath = getFilePath(path);
            if (Files.exists(filePath)) {
                // file exist
                String contentType = guessContentType(filePath);
                sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
            } else if (path.equals("/")) {

                sendResponse(client, "200 OK", "text/html", this.createHTMLPage());
            } else {
                // 404
                byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
                sendResponse(client, "404 Not Found", "text/html", notFoundContent);
            }
        }
    }

    private static void sendResponse(Socket client, String status, String contentType, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("Http_server/1.1 \r\n" + status).getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.flush();
        client.close();
    }

    private static Path getFilePath(String path) {
        if ("/".equals(path)) {
            path = "/index.html";
        }

        return Paths.get("/tmp/www", path);
    }

    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }



    public byte[] createHTMLPage(){

        StringBuilder htmlstring = new StringBuilder();
        htmlstring.append("" +
                "<!DOCTYPE html>\n"
                +"<html>\n<head>\n<title>CC - G25</title>\n</head>\n<body>\n"
                + "<h1> CC 21/22</h1>\n"
                +"<h2> Status: running...</h2>\n");

        boolean login = ftr.getChannel().isLoggedIn();

        htmlstring.append("<h3> Logged in : ").append(login).append("</h3>\n");

        List<String> friendfiles =  this.ftr.getFriend_files();
        boolean haveFriendFiles = friendfiles != null && friendfiles.size() != 0;

        htmlstring.append("<p> Have friend's files ?? [").append(haveFriendFiles).append("]</p>");
        if(haveFriendFiles){
            htmlstring.append("<ul>\n");
            for(String s : friendfiles){
                htmlstring.append("<li>").append(s).append("</li>\n");
            }
            htmlstring.append("</ul>\n");
        }

        Map<Integer, Quartet<String, Boolean, Long, Long>> requests_done = this.ftr.getRequests_done();

        if(requests_done != null){

            htmlstring.append("<h4> Requests done </h4>\n");
            htmlstring.append("<ul>\n");
            for(Map.Entry<Integer,Quartet<String, Boolean, Long, Long>> e : requests_done.entrySet()){

                htmlstring.append("<li><mark>"+ e.getValue().getValue0() + "</mark>, needed update? [<b>"
                + e.getValue().getValue1()
                + "</b>], ms: " + e.getValue().getValue2()
                + ", bytes: " + e.getValue().getValue3()
                + ", debit (bps): " + (e.getValue().getValue3() * 8)/(e.getValue().getValue2()*0.001)
                + "</li>\n");
            }
            htmlstring.append("</ul>\n");
        }

        htmlstring.append("</body>\n</html>\n");
        return htmlstring.toString().getBytes();
    }
}