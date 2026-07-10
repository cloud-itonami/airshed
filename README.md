# airshed

`com-etzhayyim-airshed` is the evidence and governance actor for the India
Clean Air System-of-Systems pilot.  It does not regulate, disburse funds, or
publish personal data.  It maintains an auditable view of an airshed's sources,
exposure, interventions, and accountable institutions, then produces proposals
for a human Airshed Council.

The first scope is Delhi-NCR / Indo-Gangetic Plain.  The unit of analysis is an
airshed, not a city: PM2.5 and its precursors cross state borders and have both
primary and secondary sources.

## Boundaries

- **Writes:** provenance-labelled observations, derived scenarios, meeting
  decisions, and public-safe activity briefs.
- **May propose:** a source-control portfolio or an alert.
- **Never executes:** a GRAP stage, permit action, payment, enforcement action,
  or release of household/facility-level data.
- **Requires a signed council decision:** every policy commitment and any
  publication that identifies a regulated facility.

The machine-readable actor contract is [actor-manifest.jsonld](actor-manifest.jsonld).
The portable data vocabulary is implemented in `kotoba-lang/airshed`.

## Collaboration and participation

The actor maintains a collaborator registry for public authorities, farmer and
worker organisations, health institutions, schools, technical organisations,
financiers, and community representatives. Participation is opt-in and records
only a public role, jurisdiction, consent status, declared conflicts, and
chosen contact channel. It does not infer political affiliation or create a
contact list from social data.

Engagements are evidence-led asks, not persuasion scores: each has a stated
purpose, audience, evidence packet, response state, and a withdrawal path.
Events are accessible deliberation or verification sessions, with published
agenda and anonymised participation counts. A session cannot create a binding
policy commitment; that requires the separate council-decision record.

## Evidence baseline

- [India NCAP status and coverage](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2147751&lang=2&reg=48)
- [CAQM, the statutory NCR coordination body](https://caqm.nic.in/)
- [World Bank: India needs airshed-wide, multi-sector coordination](https://www.worldbank.org/en/country/india/publication/catalyzing-clean-air-in-india)
