---
id: UC06
name: Register Patient Account
feature: User Access & Personal Account Management
primary_actors: [Guest]
secondary_actors: [Authentication Service, Notification Service]
br: [BR-04, BR-09, BR-10, BR-16]
msg: [MSG-04, MSG-08, MSG-09, MSG-10]
screens: [Patient Registration, Registration Verification]
source: Report 3 - section 2.1.6 (UC description in 1.3.3)
---

# UC06 - Register Patient Account

| Field            | Content                                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------------------ |
| Use Case Name    | UC06_Register Patient Account                                                                                      |
| Primary Actor    | Guest                                                                                                              |
| Secondary Actors | Authentication Service and Notification Service, if used                                                           |
| Description      | Allows a Guest to create and verify a Patient account for booking appointments and using personal clinic services. |
| Business Rules   | BR-04, BR-09, BR-10, BR-16                                                                                         |

## Preconditions

The Guest is not signed in and can receive identity verification instructions.

## Normal Flow

**N1. Register and verify a Patient account**

1. The Guest opens Patient Registration.
2. The system displays the registration form.
3. The Guest enters their identity, contact, and account information.
4. The system validates the information, unique account identifiers, and password policy [BR-10] [BR-16] [BR-04].
5. The system records the registration and provides identity verification instructions [MSG-09].
6. The Guest completes identity verification.
7. The system verifies the account and enables Patient access [BR-09] [MSG-10].

The use case ends.

## Alternative Flows

None.

## Exceptions

- **E1. Invalid registration information.** If required information is missing or invalid, the system displays [MSG-08]. The Guest may correct the information and resubmit.
- **E2. Existing identity or account.** If an identifier cannot be used, the system displays [MSG-08] without identifying an existing account. The Guest may correct the information and resubmit.
- **E3. Verification rejected.** If identity verification fails, the system displays [MSG-08]. The Guest may retry verification or restart registration.
- **E4. Service failure.** If the request cannot be completed, the system displays [MSG-04] and the use case ends.

## Postconditions

A verified Patient account is available for sign-in; an unverified registration has no Patient access.
