\newpage

# Availability

The **Availability** feature lets users indicate when they are unavailable, such as when they are out of the office. This information helps support teams understand when a reply should not be expected and provides additional context when working with tickets.


## Purpose

Availability provides a clear way to record periods when a user or an entire company is unavailable.

This makes it possible for billetsys to represent absence and company-wide closures without relying only on ticket status or manual communication.

## Personal availability

Users can create personal availability periods to indicate when they are not available.

A personal availability entry includes:

* Start date
* End date
* Reason

The start and end dates define the period during which the user is unavailable.

## Creating availability

To create a personal availability period, open the **Availability** page and add a new entry.

Select the start and end dates and optionally provide a reason for the absence.

The reason helps other users understand the context of the unavailable period.

## Updating and deleting availability

Users can update or delete their own availability entries.

Updating an entry changes the dates or reason associated with that period.

Deleting an entry removes the personal availability period from the calendar and from ticket-related availability information.

## Company-wide availability

Company-wide availability is used for periods when an entire company is unavailable.

This can be useful for:

* Company holidays
* Office closures
* Organization-wide breaks
* Other periods when the company should not be expected to respond

Company-wide availability is associated with the company rather than an individual user.

## Managing company availability

Company-wide availability can only be managed by users with the required permissions.

Support and Superuser users can manage company-wide availability for their own company, while users without the required permissions cannot create or modify these entries.

Company isolation is enforced so that company availability cannot be managed across different companies.

## Viewing other users' availability

Availability visibility depends on the user's role.

Regular users and TAMs can see their own availability together with company-wide availability.

Support and Superuser users can also view availability for other users in their company.

Users cannot view another company's user availability.

## Availability in tickets

Availability is also used as context when working with tickets.

When a ticket is associated with an unavailable user or an unavailable company, billetsys can display an availability warning to help support teams understand that a response may be delayed.

The warning provides additional context without changing the ticket itself.

## Date rules

Availability uses calendar dates rather than specific times.

The end date must not be before the start date.

A single-day availability period is valid when the start date and end date are the same.

Personal availability periods cannot overlap with another personal availability period for the same user.

## Reason

The reason is optional and can be used to explain why the user or company is unavailable.

The reason is limited to **255 characters**.

## Operational value

Availability gives support teams additional context when deciding how to handle tickets and when a response should be expected.

Together with ticket information, it helps make periods of absence and company-wide closures visible without requiring users to communicate the same information manually.

## Keyboard shortcuts

Like other list and form views in the application, the Availability page supports the application's standard keyboard navigation shortcuts.

See the **Navigation** chapter for more details.
