package com.example.ftpclienttest.data;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Locale;

import lombok.Getter;

@Getter
public class FtpPassive {

    private BufferedReader controlReader;
    private PrintWriter controlOut;
    private String passHost;
    private int passPort;
    private String username;
    private String password;
    private String response;
    private boolean isLogin = false;
    private boolean isPassMode = false;
    private Socket socket;
    public enum TransferType {
        ASCII, BINARY
    };
    private TransferType type = TransferType.BINARY;
    public enum TransferMode {
        BLOCK,STREAM,ZIP
    }
    private TransferMode mode = TransferMode.STREAM;


    private static final int PORT = 21;

    public FtpPassive(String url,String username,String password){
        try {
            this.socket = new Socket(url,PORT);
            this.username = username;
            this.password = password;
            this.controlReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.controlOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true);
            Init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void Init() throws IOException {
        String msg = "";
        do {
            msg = controlReader.readLine();
            EventBus.getDefault().post(msg);
        }while (!msg.startsWith("220 "));
    }

    public int login() throws Exception {
//        logger.debug(this.username);
        this.controlOut.println("USER "+this.username);

        this.response = this.controlReader.readLine();
//        System.out.println(response);
        EventBus.getDefault().post(response);
        if (!this.response.startsWith("331 ")){//验证成功
//             throw new IOException(""+response);
            EventBus.getDefault().post(response);
            return -1;
        }

        controlOut.println("PASS "+ password);

        response = controlReader.readLine();
        EventBus.getDefault().post(response);
        if (!response.startsWith("230 ")){
//            throw new IOException(""+response);
            EventBus.getDefault().post(response);
            return -1;
        }
        isLogin = true;
        return 1;
    }

    public int loginAnonymous() throws IOException {
        //        logger.debug(this.username);
        this.controlOut.println("USER ANONYMOUS");

        this.response = this.controlReader.readLine();
//        System.out.println(response);
        EventBus.getDefault().post(response);
        if (!this.response.startsWith("331 ")){//验证成功
//             throw new IOException(""+response);
            EventBus.getDefault().post(response);
            return -1;
        }

        controlOut.println("PASS "+ password);

        response = controlReader.readLine();
        EventBus.getDefault().post(response);
        if (!response.startsWith("230 ")){
//            throw new IOException(""+response);
            EventBus.getDefault().post(response);
            return -1;
        }
        isLogin = true;
        return 1;
    }

    public int logout() throws IOException {
        controlOut.println("QUIT");
        this.response = this.controlReader.readLine();
        if (!response.startsWith("221")){
            throw new IOException("Close connection failed!");
        }
        EventBus.getDefault().post(response);
        this.isLogin = false;
        socket.close();
        if (socket.isClosed()){
//            System.out.println("关闭连接");
            EventBus.getDefault().post("关闭连接");
            return 1;
        }
        return -1;
    }

    private void checkPassiveConnect() throws IOException {
        //是不是每次都要关服务器开放的端口
        if (!this.isPassMode){
            this.controlOut.println("PASV mode");
            response = this.controlReader.readLine();
            EventBus.getDefault().post(response);
            if (!response.startsWith("271")){
                throw new IOException("FTPClient could not request passive mode: " + response);
            }
            int tempPort = Integer.parseInt(response.split(" ")[4]);
            EventBus.getDefault().post("端口号："+tempPort);
            this.passHost = "127.0.0.1";
            this.passPort = tempPort;
            isPassMode = true;
        }
    }

    public int upload(String path) throws IOException {
        EventBus.getDefault().post("File path: "+path);
        File file = new File(path);
        if (!file.exists()) {
            EventBus.getDefault().post("File not exists...");
            return -1;
        }else {
            checkPassiveConnect();

            this.controlOut.println("STOR "+file.getName());
            Socket dataSocket = new Socket(this.passHost,this.passPort);
            response = this.controlReader.readLine();
            EventBus.getDefault().post(response);
            FileInputStream inputStream = new FileInputStream(file);
            int bytesRead;
            if (this.type == TransferType.BINARY) {
                BufferedInputStream input = new BufferedInputStream(inputStream);
                BufferedOutputStream output = new BufferedOutputStream(dataSocket.getOutputStream());
                byte[] buffer = new byte[4096];
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
                input.close();
                output.close();
            }else {
                InputStreamReader input = new InputStreamReader(inputStream);
                OutputStreamWriter output = new OutputStreamWriter(dataSocket.getOutputStream());
                char[] buffer = new char[2048];
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
                output.close();
                input.close();
            }
            inputStream.close();
            dataSocket.close();
            response = this.controlReader.readLine();
            EventBus.getDefault().post(response);
            return 1;
        }
    }

    public void uploadFold(String path) throws IOException {
        File file1 = new File(path);
        if (file1.exists()){
            File[] files = file1.listFiles();
            if (files!= null) {
                for (File file : files) {
                    if (file.isDirectory()){
                        uploadFold(file.getPath());
                    }else {
                        upload(file.getPath());
                    }
                }
            }
        }
    }

    public int download(String filename, String path) throws IOException {
         EventBus.getDefault().post(filename);
         checkPassiveConnect();
         //send RETR command
         this.controlOut.println("RETR "+filename);
         response = controlReader.readLine();
         EventBus.getDefault().post(response);
         if (!response.startsWith("150")){
            EventBus.getDefault().post("File not exits!");
            return -1;
         }
         //send data connection
         Socket dataSocket = new Socket(this.passHost,this.passPort);
         //Read data from server
        int bytesRead = 0;
        if (this.type == TransferType.BINARY) {
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(new File(path, filename)));
            BufferedInputStream input = new BufferedInputStream(dataSocket.getInputStream());
            byte[] buffer = new byte[4096];
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
            output.close();
            input.close();
        }else {
            OutputStreamWriter output = new OutputStreamWriter(new FileOutputStream(new File(path,filename)));
            InputStreamReader input = new InputStreamReader(dataSocket.getInputStream());
            char[] buffer = new char[2048];
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
            output.close();
            input.close();
        }
        dataSocket.close();
        response = controlReader.readLine();
        EventBus.getDefault().post(response);
        return 1;
    }

    public void downloadFold(String filename, String path) throws Exception {
        File file = new File(path,filename);
        file.mkdir();
        //send RETR command
        controlOut.println("RETR " + filename);
        response = controlReader.readLine();
        if (!response.startsWith("150")){
            EventBus.getDefault().post("File not exits!");
            return;
        }
        ArrayList<String> files = new ArrayList<>();
        ArrayList<String> folds = new ArrayList<>();
        String[] res;
        while ((response=controlReader.readLine()).startsWith("160")){
            EventBus.getDefault().post(response);
            res = response.split(" ");
            if (res[2].equals("file")){
                files.add(res[1]);
            }else {
                folds.add(res[1]);
            }
        }
        if (files.size()>0){
            for (String name:files) {
                download(filename+File.separator+name,path);
            }
        }
        if (folds.size()>0){
            for (String fold:folds){
                downloadFold(filename+File.separator+fold,path);
            }
        }

    }

    public void selectType(String type) throws IOException {
        type=type.toUpperCase(Locale.ROOT);
        if (type.equals("ASCII")){
            this.type = TransferType.ASCII;
            controlOut.println("TYPE ASCII");
        }else if (type.equals("BINARY")){
            this.type = TransferType.BINARY;
            controlOut.println("TYPE BINARY");
        }
        response = controlReader.readLine();
        if (!response.startsWith("280")){
            throw new IOException("Failed to set type "+response);
        }
        EventBus.getDefault().post(response);
    }

    public void selectMode(String mode) throws IOException {
        mode = mode.toUpperCase(Locale.ROOT);
        switch (mode) {
            case "BLOCK" : this.mode = TransferMode.BLOCK;break;
            case "STREAM" : this.mode = TransferMode.STREAM;break;
            case "ZIP" : this.mode = TransferMode.ZIP;break;
        }
        controlOut.println("MODE "+mode);
        response = controlReader.readLine();
        if (!response.startsWith("290")){
            throw new IOException("Failed to set mode "+response);
        }
        EventBus.getDefault().post(response);
    }

    public void selectStructure(String stru) throws IOException {
        stru = stru.toUpperCase(Locale.ROOT);
        String order="";
        switch (stru) {
            case "R" : order = "RECORD";break;
            case "P" : order = "PAGE";break;
            default : order = "FILE";
        }
        controlOut.println("STRU "+order);
        response = controlReader.readLine();
        if (!response.startsWith("295")){
            throw new IOException("Failed to set structure "+response);
        }
        EventBus.getDefault().post(response);
    }
}
