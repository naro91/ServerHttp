import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by Abovyan Narek on 08.03.15.
 */
public class ClientSession implements Runnable {
    private static final String DEFAULT_PATH = "DOCUMENT_ROOT";
    private Socket socket;
    private InputStream is;
    private OutputStream os;
    public ClientSession(Socket socket) {
        this.socket = socket;
        try {
            this.is = socket.getInputStream();
            this.os = socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void run() {
        try {
            System.out.println("queries the server on port " + HttpServer.CURRENT_PORT + " port");
            String header = readHeader(); // чтение header
            String method = readMethod(header); // получаем метод запроса из header
            if(!method.equals("GET") && !method.equals("HEAD")) {
                methodNotAllowed();
            } else {
                methodAllowed(method, header);
            }
        } catch (IOException e) {
        } catch (NullPointerException e) {
        } finally{
            try {
                socket.close();
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    private void methodAllowed(String method, String header) throws IOException {
        String url = findFilePath(header); // получаем путь к запрашиваемому файлу из header
        int status;
        if (url.charAt(url.length()-1) == '/') { // если путь заканчивается на / ищем index.html
            url=url+"index.html";
            if (fileExists(url)) { // если файл существует устанавливаем статус ответа 200
                status = 200;
            } else status = 403;
        } else  {
            int from;
            from = url.length()-1;
            int i  = from;
            boolean pointIsFound = false;
            while (i>=0 && !pointIsFound) {  // находим позицию . после которой идет расширение файла
                if (url.charAt(i) == '.'){
                    pointIsFound = true;
                }
                i--;
            }
            if (!pointIsFound) {  // если точка не найдена ищем файл index.html в директории
                url = url+"/index.html";
                if (fileExists(url)) { // если файл существует устанавливаем статус 200 ответа
                    status = 200;
                } else status = 403;
            } else status = getStatus(DEFAULT_PATH + url); // устанавливаем статус ответа в зависимости от существования запрашиваемого файла
            if (status == 403 && (url.indexOf("/index.html") !=-1) ) { // если в запросе есть подстрока index.html отбрасываем остальную часть
                url = url.substring(0,url.indexOf("/index.html"));     // запроса и проверяем существование index.html
                if (fileExists(url)) { // если файл существует устанавливаем статус ответа 200
                    status = 200;
                }
            }
        }
        long contentLength = getContentLength(url); // получаем размер файла
        String contentType = getContentType(url);  // получаем тип возвращаемого контента
        String responseHeader = creatingHeader(status, contentLength, contentType); // создаем заголовок ответа
        PrintStream answer = new PrintStream(os, true, "utf-8");
        answer.print(responseHeader);
        if(method.equals("GET") && status == 200) {
            InputStream inputStream = ClientSession.class.getResourceAsStream(DEFAULT_PATH+url);
            int count = 0;
            byte[] bytes = new byte[1024];
            while((count = inputStream.read(bytes)) != -1) {
                os.write(bytes);
            }
        }
    }

    private void methodNotAllowed() throws UnsupportedEncodingException { // формирует ответ при отсутствии поддержки метода запроса
        StringBuffer buffer = new StringBuffer();
        buffer.append("HTTP/1.1 405 Method Not Allowed\n");
        buffer.append("Server: JavaServer\n");
        buffer.append("Date: " + new SimpleDateFormat("yyyyy-mm-dd hh:mm:ss").format(new Date())+"\n");
        buffer.append("Connection: close\r\n\r\n");
        PrintStream answer = new PrintStream(os, true, "utf-8");
        answer.print(buffer.toString());
    }

    private String readMethod(String firstLine) { // возвращает метод запроса
        int from = 0;
        int to = firstLine.indexOf(" ");
        return from <= to ? firstLine.substring(from,to) : null;
    }

    private String readHeader() throws IOException{ // возвращает header запроса в виде строки
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String ln = null;
        ln = reader.readLine();
        while (ln != null && ln.length() != 0) {
            builder.append(ln+ "\n");
            ln = reader.readLine();
        }
        return builder.toString();
    }
    private String findFilePath(String header) { // возвращает путь к файлу с удаленными недопустимыми символами

        String url = getUrl(header);
        url = myUrlDecoder(url);
        url = removeQuery(url);
        url = removeDepricatedSymbols(url);
        return url;
    }
    private String removeQuery(String url) { // удаляет из пути get параметры запроса
        if (url.indexOf('?') != -1) {
            url = url.substring(0,url.indexOf('?'));
        }
        return url;
    }
    private String myUrlDecoder(String url) { // декодирует запрос в utf-8
        try {
            url = URLDecoder.decode(url, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return url;
    }
    private String removeDepricatedSymbols(String url) { // удаление недопустимых символов
        boolean checked = false;
        int from;
        while (!checked) {
            if((from = url.indexOf("../") )!=-1) {
                url = url.substring(0,from)+url.substring(from+3,url.length());
            } else checked = true;
        }
        return url;
    }
    private String getUrl(String header) { // возвращает путь к запрашиваемому файлу
        int from = header.indexOf(" ")+1;
        int to = header.indexOf("HTTP/1.", from)-1;
        return header.substring(from, to);
    }
    private boolean fileExists(String url) { // возвращает true если файл существует
        InputStream inputStream = ClientSession.class.getResourceAsStream(DEFAULT_PATH+url);
        return inputStream != null ? true : false;
    }
    private int getStatus(String url) { // возвращает код 200 если файл существует
        InputStream inputStream = ClientSession.class.getResourceAsStream(url);
        return inputStream != null ? 200 : 404;
    }

    private String creatingHeader(int status, long contentLength, String contentType) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("HTTP/1.0 " + status + getStatusDescription(status)+"\r\n");
        buffer.append("Server: JavaServer\r\n");
//        SimpleDateFormat dateFormat = new .setTimeZone(TimeZone.getTimeZone("GMT"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        buffer.append("Date: " + dateFormat.format(new Date())+"\r\n");
        buffer.append("Content-Length: " + contentLength +"\r\n");
        buffer.append("Content-Type: " + contentType +"\r\n");
        buffer.append("Connection: close\r\n\r\n");
        return buffer.toString();
    }
    private String getStatusDescription(int status) {
        if (status ==  200 )
            return " OK";
        if (status == 404)
            return " Not Found";
        if (status == 403)
            return " Forbidden";
        else return " ";
    }
    private String getContentType(String url) {  // возвращает полное имя контента по url
        int len = url.length()-1;
        int i = len;
        String ct = null;
        boolean ctFinded = false;
        while (i >= 0 && !ctFinded) {
            if (url.charAt(i) == '.') {
                ctFinded = true;
                ct = url.substring(i+1,len+1);
            }
            i--;
        }
        if(!ctFinded) {
            ct = "bin file";
        }
        return getFullContentType(ct);
    }
    private String getFullContentType(String end) {  // возвращает полное имя контента по расширению
        switch (end) {
            case "css": return "text/css";
            case "gif": return "image/gif";
            case "html": return "text/html";
            case "jpeg": return "image/jpeg";
            case "jpg": return "image/jpeg";
            case "js": return "text/javascript";
            case "png": return "image/png";
            case "swf": return "application/x-shockwave-flash";
            default: return end;
        }
    }
    private long getContentLength(String url) { // возвращает размер файла
        String filePath = "./src/main/resources/"+DEFAULT_PATH+url;
        File requstedFile = new File(filePath);
        return requstedFile.length();
    }
}