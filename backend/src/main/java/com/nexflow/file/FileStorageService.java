package com.nexflow.file;

import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import com.nexflow.config.AppProperties;
import com.nexflow.ticket.TicketAttachment;
import com.nexflow.ticket.TicketAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppProperties props;
    private final TicketAttachmentRepository attachmentRepository;

    public AttachmentInfo store(MultipartFile file, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件为空");
        }
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            extension = originalName.substring(dot).toLowerCase();
            if (extension.length() > 10) {
                extension = "";
            }
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path dir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
            }
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件保存失败");
        }
        TicketAttachment attachment = new TicketAttachment();
        attachment.setStoredName(storedName);
        attachment.setFileName(originalName);
        attachment.setContentType(file.getContentType());
        attachment.setSize(file.getSize());
        attachment.setUploadedById(uploaderId);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentRepository.save(attachment);
        return new AttachmentInfo(attachment.getId(), originalName, file.getContentType(), file.getSize());
    }

    public Path resolve(TicketAttachment attachment) {
        Path dir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(attachment.getStoredName()).normalize();
        if (!target.startsWith(dir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        return target;
    }

    public record AttachmentInfo(Long id, String fileName, String contentType, long size) {
    }
}
