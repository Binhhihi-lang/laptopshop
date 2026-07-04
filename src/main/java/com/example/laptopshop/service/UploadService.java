package com.example.laptopshop.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletContext;

@Service
public class UploadService {
    private final ServletContext servletContext;

    // Lấy đường dẫn từ application.properties
    @Value("${upload.directory}")
    private String uploadDirectory;

    public UploadService(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    // xử lý upload file
    public String handleSaveUploadFile(MultipartFile file, String targetFolder) {
        String finalName = "";
        try {
            byte[] bytes = file.getBytes();
            // lấy đường dẫn thực tế trong thư mục webapp

            // save file vào thư mục avatar /
            File dir = new File(uploadDirectory + File.separator + targetFolder);
            if (!dir.exists()) {
                dir.mkdirs(); // linux
            }
            // Tạo file mới trong server
            finalName = System.currentTimeMillis() + "-" + file.getOriginalFilename();
            File serverFile = new File(dir.getAbsolutePath() + File.separator + finalName);
            // logic save file
            BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
            stream.write(bytes);
            stream.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return finalName;
    }

    // xử lý xóa file ảnh
    // lấy tên file và thư mục chứa file để xóa
    public void handleDeleteFile(String fileName, String targetFolder) {
        File file = new File(uploadDirectory + File.separator + targetFolder + File.separator + fileName);
        if (file.exists()) {
            file.delete();
        } else {
            System.out.println("Không tìm thấy file để xóa");
        }
    }
}
