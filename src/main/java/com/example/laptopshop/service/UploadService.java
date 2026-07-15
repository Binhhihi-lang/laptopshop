package com.example.laptopshop.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class UploadService {

    private final Cloudinary cloudinary;

    // Inject bean Cloudinary vào thông qua Constructor
    public UploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Hàm upload ảnh lên Cloudinary và trả về URL
     * 
     * @param file       File ảnh lấy từ request
     * @param folderName Tên thư mục trên Cloudinary để lưu ảnh (vd: "category",
     *                   "product")
     * @return URL online của ảnh, hoặc null nếu file rỗng
     */
    public String handleSaveUploadFile(MultipartFile file, String folderName) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // Upload file lên Cloudinary và đưa vào folder mong muốn
            Map uploadResult = this.cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", folderName));

            // Trích xuất và trả về URL bảo mật (https) của ảnh vừa upload
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new RuntimeException("Tải ảnh lên Cloudinary thất bại!", e);
        }
    }

    /**
     * Hàm xóa ảnh trên Cloudinary khi bạn cập nhật hoặc xóa thực thể
     * 
     * @param imageUrl URL đầy đủ của bức ảnh đang lưu trong DB
     */

    public void handleDeleteFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        try {
            // Cloudinary cần public_id để xóa (ví dụ: "category/abcxyz123")
            // Đoạn logic dưới đây giúp bạn bóc tách nhanh public_id từ URL online
            String publicId = extractPublicId(imageUrl);

            if (publicId != null) {
                this.cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (IOException e) {
            System.err.println("Không thể xóa ảnh cũ trên Cloudinary: " + e.getMessage());
        }
    }

    // Helper bóc tách public_id từ URL dạng:
    // https://res.cloudinary.com/.../upload/v12345/folder/name.jpg
    
    private String extractPublicId(String imageUrl) {
        try {
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1)
                return null;

            // Cắt chuỗi từ sau chữ "/upload/vXXXXXXXX/"
            String subStr = imageUrl.substring(uploadIndex + 8);
            int versionIndex = subStr.indexOf("/");
            if (subStr.charAt(0) == 'v' && versionIndex != -1) {
                subStr = subStr.substring(versionIndex + 1);
            }

            // Bỏ phần đuôi mở rộng file (.jpg, .png...)
            int dotIndex = subStr.lastIndexOf(".");
            if (dotIndex != -1) {
                subStr = subStr.substring(0, dotIndex);
            }
            return subStr;
        } catch (Exception e) {
            return null;
        }
    }
}