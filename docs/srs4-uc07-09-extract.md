# Trích Specification UC07–UC09 từ SRS (4)

Nguồn: FA26_SEP490_G58_Report3_Software_Requirement_Specification (4).docx. Trích văn bản theo thứ tự bảng; không thay thế định dạng tài liệu gốc. Yêu cầu người dùng ngày 30/10/2026 lấy tài liệu này làm chuẩn cho frontend Chủ phòng khám.

2.2.1 UC-07 Create Clinic Staff Account

a. Screen Layouts



b. Specification

Use Case Name

UC07_Create Clinic Staff Account

Primary Actor

Administrator; Clinic Owner when creating an Administrator account

Secondary Actors

None

Description

Allows an Administrator to create username accounts with an initial password for the fixed Clinic Staff roles. The Clinic Owner creates Administrator accounts.

Preconditions

The actor is signed in.

Normal Flow

N1. Create a Clinic Staff account

1. The Administrator selects Create Account.

2. The system offers the fixed Clinic Staff roles [BR-08].

3. The Administrator selects a role and enters the new user's username and an initial password.

4. The system validates the information, the unique username, and Administrator authority [BR-08] [BR-11].

5. The system creates the account, requires a password change at first sign-in, and shows the initial password once to the Administrator, who gives it to the staff member in person [BR-07] [BR-11].

6. The system confirms the created account [MSG-11].

The use case ends.

Alternative Flows

AF1. Create an Administrator account. The Clinic Owner selects Create Account.

1. The system offers the Administrator account type [BR-08].

2. The Clinic Owner enters the account information.

3. The system validates the information and the unique username, creates the account with an initial password that must be changed at first sign-in, and shows the password once to the Clinic Owner [BR-07] [BR-11].

4. The system displays the created account [MSG-11]. The use case ends.

Exceptions

E1. Invalid account information. If required information is invalid, the system displays [MSG-12]. The actor may correct the information and resubmit.

E2. Username unavailable. If the username is already used, the system displays [MSG-13]. The actor may enter another username and resubmit.

E3. Account type not permitted. If the actor may not create the selected type, the system displays [MSG-14] and the use case ends.

E4. Creation failure. If the account cannot be created, the system displays [MSG-16] and the use case ends.

Postconditions

An account of the permitted type exists, and its user must change the initial password at first sign-in.

Business Rules

BR-07, BR-08, BR-11

2.2.2 UC-08 Edit Account Status

a. Screen Layouts



b. Specification

Use Case Name

UC08_Edit Account Status

Primary Actor

Administrator; Clinic Owner for Administrator accounts

Secondary Actors

None

Description

Allows an Administrator to activate or deactivate Patient and Clinic Staff accounts in their scope and to reissue a forgotten Clinic Staff password. The Clinic Owner does the same for Administrator accounts.

Preconditions

The actor is signed in.

Normal Flow

N1. Change an account's status

1. The Administrator opens a Patient or Clinic Staff account.

2. The system displays its current status and available actions.

3. The Administrator selects Activate or Deactivate.

4. The system checks the actor's authority, account scope, and requested change [BR-08].

5. The system updates access without deleting the account or its linked records [BR-10].

6. The system displays the new status [MSG-15].

The use case ends.

Alternative Flows

AF1. Clinic Owner controls an Administrator account. The Clinic Owner opens an Administrator account. The system displays its status, and the Clinic Owner selects a permitted status action or reissues the password as in AF2 [BR-08].

AF2. Reissue a forgotten password. A Clinic Staff member who has forgotten their password asks the Administrator; an Administrator asks the Clinic Owner. The actor opens the account and selects Reissue Password. The system sets a new initial password, shows it once to the actor, and requires the user to change it at the next sign-in [BR-07] [BR-08]. A forgotten Clinic Owner password is reset by technical support outside the system.

Exceptions

E1. Access denied or account unavailable. If the actor lacks authority or the account is unavailable, the system displays [MSG-14] and the use case ends.

E2. Status change unavailable. If the requested change is not allowed for the current status, the system displays [MSG-42]. The actor may revise the request and try again.

E3. Update failure. If the change cannot be completed, the system displays [MSG-16] and the use case ends.

Postconditions

The account has the selected permitted status; its linked records remain available under their access rules.

Business Rules

BR-07, BR-08, BR-10

2.2.3 UC-09 View User Accounts

a. Screen Layouts



b. Specification

Use Case Name

UC09_View User Accounts

Primary Actor

Administrator; Clinic Owner for Administrator accounts

Secondary Actors

None

Description

Allows Administrators to search and view Patient and Clinic Staff accounts in their scope, including account type, status, and applicable basic Clinic Staff profile details. The Clinic Owner can view Administrator accounts.

Preconditions

The actor is signed in.

Normal Flow

N1. Search and view user accounts

1. The Administrator opens User Accounts.

2. The system shows only the account types and records within the Administrator's scope [BR-08].

3. The Administrator enters search criteria.

4. The system validates the criteria.

5. The system displays matching permitted accounts with their type and status [BR-08].

6. The Administrator selects an account.

7. The system checks access and displays the account details, including permitted basic Clinic Staff profile details where applicable [BR-09] [BR-08].

The use case ends.

Alternative Flows

AF1. Clinic Owner views Administrator accounts. The Clinic Owner opens User Accounts. The system limits the directory to Administrator accounts, which the Clinic Owner may search and view [BR-08].

AF2. No matching accounts. If no permitted account matches, the system displays an empty result. The actor may revise the search or end the use case.

Exceptions

E1. Invalid search criteria. If the criteria are invalid, the system displays [MSG-54]. The actor may revise the request and try again.

E2. Access denied or account unavailable. If the actor cannot view the selected record or it is unavailable, the system displays [MSG-14] and the use case ends.

E3. Directory failure. If the information cannot be retrieved, the system displays [MSG-16] and the use case ends.

Postconditions

The actor has viewed only permitted account information; no account data changes.

Business Rules

BR-08, BR-09
