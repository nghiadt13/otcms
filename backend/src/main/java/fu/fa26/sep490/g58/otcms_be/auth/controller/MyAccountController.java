package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Account of the caller (any signed-in role); the account id comes from the verified token only. */
@RestController
public class MyAccountController {
    private final MyAccountQueryService myAccount;

    public MyAccountController(MyAccountQueryService myAccount) {
        this.myAccount = myAccount;
    }

    @GetMapping("/api/v1/me")
    public MyAccountResponse me(@AuthenticationPrincipal AuthenticatedAccount account) {
        if (account == null) throw new SessionException("AUTHENTICATION_REQUIRED");
        var view = myAccount.find(account.accountId())
                .orElseThrow(() -> new SessionException("AUTHENTICATION_REQUIRED"));
        return new MyAccountResponse(view.id(), view.accountType(), view.email(), view.displayName());
    }

    public record MyAccountResponse(UUID id, String accountType, String email, String displayName) { }
}
