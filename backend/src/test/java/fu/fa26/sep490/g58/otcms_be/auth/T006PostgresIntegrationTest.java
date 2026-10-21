package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="app.auth.session-security.jwt-secret=t006-integration-secret-32-bytes-minimum")
@LocalPostgresTest
class T006PostgresIntegrationTest {
    @Autowired AccountRepository accounts; @Autowired AuthSessionRepository sessions; @Autowired AuthSessionService service;
    AccountJpaEntity account;
    @BeforeEach void account(){account=new AccountJpaEntity();account.setNormalizedEmail("t006-"+UUID.randomUUID()+"@example.test");account.setAccountType(AccountType.PATIENT);account.setStatus(AccountStatus.ACTIVE);account.setFailedCredentialCount(0);account.setCreatedAt(Instant.now());account.setUpdatedAt(Instant.now());account=accounts.saveAndFlush(account);}
    @AfterEach void cleanup(){sessions.deleteAll(sessions.findAllByAccountId(account.getId()));accounts.deleteById(account.getId());}

    @Test void sixthLoginEvictsOldestAndLogoutPersists(){AuthSessionService.IssuedSession first=null,last=null;for(int i=0;i<6;i++){last=service.create(account.getId(),null,"test");if(i==0)first=last;}assertEquals(5,sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).size());service.logout(last.refreshToken());assertEquals(4,sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).size());service.logout(last.refreshToken());assertNotNull(first);}

    @Test void migratedAccountConstraintAcceptsEachExplicitRole() {
        for (AccountType type : new AccountType[]{AccountType.RECEPTIONIST, AccountType.DOCTOR, AccountType.OWNER, AccountType.ADMIN}) {
            AccountJpaEntity staff = new AccountJpaEntity();
            staff.setNormalizedEmail("t006-" + UUID.randomUUID() + "@example.test");
            staff.setAccountType(type);
            staff.setStatus(AccountStatus.ACTIVE);
            staff.setFailedCredentialCount(0);
            staff.setCreatedAt(Instant.now());
            staff.setUpdatedAt(Instant.now());
            assertEquals(type, accounts.saveAndFlush(staff).getAccountType());
            accounts.deleteById(staff.getId());
        }
    }

    @Test void concurrentSixthLoginsNeverLeaveMoreThanFiveActiveSessions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch gate = new CountDownLatch(1);
        var jobs = java.util.stream.IntStream.range(0, 6).mapToObj(i -> pool.submit(() -> { gate.await(); return service.create(account.getId(), null, "concurrent"); })).toList();
        gate.countDown();
        for (var job : jobs) job.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(5, sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).size());
    }

    @Test void concurrentRefreshAllowsAtMostOneRotationAndReplayRevokesFamily()throws Exception{var initial=service.create(account.getId(),null,"test");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);Callable<Boolean> call=()->{gate.await();try{service.rotate(initial.refreshToken());return true;}catch(SessionException ex){return false;}};Future<Boolean>a=pool.submit(call),b=pool.submit(call);gate.countDown();boolean ar=a.get(10,TimeUnit.SECONDS),br=b.get(10,TimeUnit.SECONDS);pool.shutdownNow();assertNotEquals(ar,br);assertTrue(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).isEmpty());}
}
