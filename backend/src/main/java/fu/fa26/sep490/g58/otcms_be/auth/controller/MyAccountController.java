package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.MyAccountDtos.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Account of the caller (any signed-in role); the account id comes from the verified token only. */
@RestController
public class MyAccountController {
    private final MyAccountService myAccount;
    private final EmailChangeService emails;

    public MyAccountController(MyAccountService myAccount, EmailChangeService emails) {
        this.myAccount = myAccount;
        this.emails = emails;
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<MyAccountQueryService.MyAccountView> me(@AuthenticationPrincipal AuthenticatedAccount account) {
        return projection(myAccount.get(account));
    }

    @PatchMapping("/api/v1/me")
    public ResponseEntity<MyAccountQueryService.MyAccountView> update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody DisplayNameRequest request) {
        return projection(myAccount.updateDisplayName(actor, request.getDisplayName()));
    }

    @PutMapping(value = "/api/v1/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MyAccountQueryService.MyAccountView> upload(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestPart("photo") MultipartFile photo) throws java.io.IOException {
        return projection(myAccount.updateAvatar(actor, photo.getBytes(), photo.getContentType()));
    }

    @DeleteMapping("/api/v1/me/avatar")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthenticatedAccount actor) {
        myAccount.removeAvatar(actor);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @GetMapping("/api/v1/accounts/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
        var avatar = myAccount.avatar(actor, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(avatar.contentType())).body(avatar.content());
    }

    @PostMapping("/api/v1/me/email-change")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<Message> requestEmail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody EmailChangeRequest request) {
        emails.request(actor, request.getNewEmail());
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body(new Message("MSG-06"));
    }

    @PostMapping("/api/v1/me/email-change/verify")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<MyAccountQueryService.MyAccountView> verifyEmail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody VerifyEmailRequest request) {
        emails.verify(actor, request.getCode());
        return projection(myAccount.get(actor));
    }

    private ResponseEntity<MyAccountQueryService.MyAccountView> projection(MyAccountQueryService.MyAccountView view) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(view);
    }
}
