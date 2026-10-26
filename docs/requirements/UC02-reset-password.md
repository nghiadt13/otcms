---
id: UC02
name: Reset Password
feature: User Access & Personal Account Management
primary_actors: [Registered User]
secondary_actors: [Authentication Service, Notification Service]
br: [BR-03, BR-04, BR-08]
msg: [MSG-02, MSG-03, MSG-04, MSG-08]
screens:
  [Password Recovery, Recovery Verification, Reset Password, Forgot Password]
source: Report 3 - section 2.1.2 (UC description in 1.3.3)
---

# UC02 - Reset Password

| Field            | Content                                                                                                |
| ---------------- | ------------------------------------------------------------------------------------------------------ |
| Use Case Name    | UC02_Reset Password                                                                                    |
| Primary Actor    | Registered User                                                                                        |
| Secondary Actors | Authentication Service and Notification Service, if used                                               |
| Description      | Allows a registered user to confirm their identity through their email address and set a new password. |
| Business Rules   | BR-03, BR-04, BR-08                                                                                    |

## Preconditions

1. The user is not signed in.
2. The user can access the email address associated with the account.

## Normal Flow

**N1. Reset password successfully**

1. The user selects Reset Password.
2. The system asks for the account email address.
3. The user enters an email address and requests recovery.
4. The system accepts the request and provides verification instructions without disclosing whether the account exists [BR-03] [MSG-02].
5. The user follows the instructions, confirms their identity, and enters a new password.
6. The system verifies the confirmation and password policy, changes the password through its credential authority, and confirms success [BR-04] [BR-08] [MSG-03].

The use case ends.

## Alternative Flows

None.

## Exceptions

- **E1. Invalid email input.** If the email is missing or malformed, the system displays [MSG-08]. The user may correct the email and resubmit.
- **E2. Confirmation rejected.** If the identity confirmation is invalid, the system displays [MSG-04]. The user may restart password recovery.
- **E3. Password rejected.** If the new password does not meet the applicable policy, the system displays [MSG-08]. The user may enter another password.
- **E4. Service failure.** If the request cannot be completed, the system displays [MSG-04] and the use case ends.

## Postconditions

After successful confirmation, the account has a new password and the user can sign in with it.
