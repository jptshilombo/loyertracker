# Gate 6 — DevSecOps

> Frontière entre la phase **07 (DevSecOps)** et la phase **08 (Développement)** industrialisé.

## Conditions d'entrée
- Gate 5 statué Go.
- Backlog et sprint 1 prêts.

## Livrables obligatoires
- [ ] Pipeline CI/CD documenté et opérationnel.
- [ ] `checklists/checklist-devops.md` renseignée.
- [ ] `checklists/checklist-securite.md` renseignée.

## Critères de validation
- [ ] CI : build + tests + quality gate automatisés.
- [ ] Sécurité intégrée : SAST, SCA, scan de secrets, scan d'images.
- [ ] Environnements `dev`/`staging`/`prod` configurés.
- [ ] Gestion des secrets hors dépôt.
- [ ] Authentification/autorisation (Keycloak) opérationnelle.
- [ ] Supervision/logs de base en place.
- [ ] Score ≥ 14/20 recommandé.

## Risques bloquants
- Absence de contrôles de sécurité automatisés.
- Secrets stockés dans le dépôt.
- Pipeline non reproductible / déploiement manuel non maîtrisé.

## Décision
- Score de maturité : ____ /20
- Décision : ☐ ✅ Go ☐ 🟠 Go sous réserve ☐ ⛔ No Go
- Réserves / actions correctives (datées) : {{…}}
- Date & responsable : {{…}}
