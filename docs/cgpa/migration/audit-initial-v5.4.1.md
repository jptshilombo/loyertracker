# Audit initial de migration CGPA v5.4.1 — LoyerTracker

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Source | CGPA v5.4 |
| Cible | CGPA v5.4.1 |
| Phase courante | Phase 7 — Développement post-go-live |
| Décision d'audit | GO sous réserve |

## Périmètre audité

- Structure : backend Spring Boot, frontend Angular, Docker Compose, infrastructure, GitHub
  Actions et documentation CGPA.
- Gates : historique 0 à 10, Gates Staging/Production v5.3 et `STG-ISOL-01` conservés.
- États : `STAGING_READY`, `STAGING_DEPLOYED`, `PRODUCTION_READY` et
  `PRODUCTION_DEPLOYED` présents.
- Delivery : Production `1.1.0` déployée ; aucun Sprint actif ; Sprint 3 Patrimoine non démarré.
- CI/CD : build, tests, SonarQube, CodeQL, Gitleaks, Trivy et packaging GHCR.
- Staging : stack `loyertracker-staging`, réseau et volume dédiés, ports paramétrables, reverse
  proxy mutualisé, rollback par tag immuable.

## Écart v5.4.1

L'ADR v5.4 existe sous
`docs/cgpa/05-architecture-conception/adr/ADR-STG-001-isolation-staging-partage.md`, tandis que
v5.4.1 impose `docs/cgpa/adr/ADR-STG-001-staging-isolation.md` comme artefact canonique.

La checklist existe déjà au chemin canonique, mais doit identifier explicitement l'auditeur
DevSecOps Lead, la validation Release Manager, les preuves et le verdict daté.

## Risques suivis

- `RSV-STG-01` : confirmation live avant/après déploiement encore à produire.
- `RSV-STG-02` : collision future de namespace, réseau, volume ou port.
- `RSV-STG-03` : introduction future d'une commande Docker globale/destructrice.
- `RSV-STG-04` : dérive du reverse proxy ou de l'inventaire des ressources partagées.

## Conclusion

Migration documentaire corrective autorisée. Aucun Gate n'est rejoué et aucune modification
applicative ou d'infrastructure n'est requise.
