package com.nexflow.file;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketAttachment;
import com.nexflow.ticket.TicketAttachmentRepository;
import com.nexflow.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storageService;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;

    @PostMapping
    public ApiResponse<FileStorageService.AttachmentInfo> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(storageService.store(file, CurrentUser.id()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        TicketAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
        checkPermission(attachment);
        Resource resource = new FileSystemResource(storageService.resolve(attachment));
        String encodedName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"")
                .body(resource);
    }

    private void checkPermission(TicketAttachment attachment) {
        Long userId = CurrentUser.id();
        var user = CurrentUser.get();
        if (attachment.getUploadedById().equals(userId) || user.hasRole("ADMIN")) {
            return;
        }
        if (attachment.getTicketId() != null) {
            Ticket ticket = ticketRepository.findById(attachment.getTicketId()).orElse(null);
            if (ticket != null) {
                boolean assignee = ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(userId);
                boolean sameDept = ticket.getGroupDeptId() != null && ticket.getGroupDeptId().equals(user.getDeptId());
                if (assignee || sameDept) {
                    return;
                }
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: 仅上传者/处理人/同部门/管理员可下载附件");
    }
}
