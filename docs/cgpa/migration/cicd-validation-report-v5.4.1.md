# Rapport de validation CI/CD — CGPA v5.4.1

| Domaine | Résultat |
|---|---|
| GitHub Actions | Conforme : `ci.yml`, `codeql.yml` |
| Backend | `mvn verify` et SonarQube bloquant configurés |
| Frontend | lint, build, tests et SonarQube bloquant configurés |
| Sécurité | CodeQL, Gitleaks, Trivy dépendances/images |
| Artefacts | Images GHCR avec tag immuable `sha-<8>` |
| Rollback | Retour ciblé au tag précédent documenté |
| Isolation Docker | Projet `loyertracker-staging`, réseau/volume dédiés |
| Ports / reverse proxy | Ports web paramétrables, routage DNS mutualisé |
| Commandes destructives | Aucune commande globale détectée dans workflows, scripts ou runbook |
| Preuve `STG-ISOL-01` | Conforme documentairement ; preuve live à renouveler |

Le pipeline ne déploie pas automatiquement sur le Docker Host mutualisé. Les opérations Staging
restent manuelles, ciblées et gouvernées par Gate.

Décision : **GO sous réserve** de `RSV-STG-01`. Aucun build ni test applicatif n'a été relancé,
la migration étant exclusivement documentaire.
