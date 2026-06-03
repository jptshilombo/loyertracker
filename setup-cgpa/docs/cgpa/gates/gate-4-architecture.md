# Gate 4 — Architecture & conception

> Frontière entre la phase **05 (Architecture)** et la phase **06 (Planification Agile)**.
> ⚠️ **Dernier verrou de codage** : c'est le gate qui, une fois Go, autorise le développement applicatif.

## Conditions d'entrée
- Gate 3 statué Go (ou Go sous réserve).
- CDC validé.

## Livrables obligatoires
- [ ] `templates/dossier-architecture.md` complété (composants, données, API, ADR).
- [ ] `checklists/checklist-architecture.md` renseignée.

## Critères de validation
- [ ] Architecture tracée vers le CDC.
- [ ] Exigences non fonctionnelles couvertes.
- [ ] Sécurité by design (Keycloak/OIDC, secrets, surface d'attaque).
- [ ] Modèle de données et contrats d'API définis.
- [ ] Stratégie de déploiement (Docker, environnements) définie.
- [ ] Décisions documentées (ADR). Score ≥ 14/20 recommandé (dernier verrou).

## Risques bloquants
- Conception non traçable au CDC.
- Sécurité ou déploiement non traités.
- Risques techniques majeurs non mitigés.

## Décision
- Score de maturité : ____ /20
- Décision : ☐ ✅ Go ☐ 🟠 Go sous réserve ☐ ⛔ No Go
- **Autorisation de coder :** ☐ Oui (si Go) ☐ Non
- Réserves / actions correctives (datées) : {{…}}
- Date & responsable : {{…}}
