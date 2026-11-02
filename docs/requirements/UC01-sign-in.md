---
id: UC01
name: Sign In
feature: User Access & Personal Account Management
primary_actors:
  [Registered User (Patient, Clinic Staff, Administrator, Clinic Owner)]
secondary_actors: [Authentication Service]
br: [BR-01, BR-02]
msg: [MSG-01, MSG-04]
screens: [Patient Login, Staff Login, User Login]
source: Report 3 - section 2.1.1 (UC description in 1.3.3)
---

# UC01 - Sign In

| Field            | Content                                                                               |
| ---------------- | ------------------------------------------------------------------------------------- |
| Use Case Name    | UC01_Sign In                                                                          |
| Primary Actor    | Registered User (Patient, Clinic Staff, Administrator, or Clinic Owner)               |
| Secondary Actors | Authentication Service, if used                                                       |
| Description      | Allows a registered user to sign in and access functions permitted for their account. |
| Business Rules   | BR-01, BR-02                                                                          |

## Preconditions

- The user has a registered account.
- The user is not signed in.

## Normal Flow

**N1. Sign in successfully**

1. The user opens Sign In.
2. The system displays the sign-in form.
3. The user enters their email and password and submits the form.
4. The system verifies the credentials and account eligibility [BR-02].
5. The system opens the functions permitted for the user's role [BR-01].

The use case ends.

## Alternative Flows

None.

## Exceptions

- **E1. Missing or invalid input.** If required information is missing or invalid, the system displays [MSG-01]. The user may correct the form and resubmit.
- **E2. Sign-in rejected.** If the credentials are incorrect or the account is ineligible, the system displays [MSG-01]. The user may correct the information and resubmit.
- **E3. Service failure.** If verification cannot be completed, the system displays [MSG-04] and the use case ends.

## Postconditions

The user is signed in and can access functions permitted for their role.
