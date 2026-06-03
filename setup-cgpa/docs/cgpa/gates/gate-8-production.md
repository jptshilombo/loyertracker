# Gate 8 — Production (mise en production)

> Frontière entre la phase **10 (Production)** et la phase **11 (Exploitation)**.

## Conditions d'entrée
- Gate 7 statué Go (recette prononcée).
- Build de production validé en CI.

## Livrables obligatoires
- [ ] `templates/checklist-production.md` complétée.
- [ ] `checklists/checklist-production.md` renseignée.
- [ ] `templates/rapport-mise-production.md` préparé.

## Critères de validation
- [ ] Aucune anomalie bloquante.
- [ ] Aucun secret en clair ; scans sécurité sans vulnérabilité critique.
- [ ] Sauvegarde réalisée ; plan de migration validé.
- [ ] Plan de rollback documenté et testé.
- [ ] Monitoring & alerting opérationnels.
- [ ] Stratégie de déploiement et fenêtre définies.
- [ ] Score ≥ 14/20 recommandé.

## Risques bloquants
- Absence de plan de rollback.
- Pas de sauvegarde / supervision.
- Vulnérabilité critique non corrigée.

## Décision
- Score de maturité : ____ /20
- Décision : ☐ ✅ Go ☐ 🟠 Go sous réserve ☐ ⛔ No Go
- Réserves / actions correctives (datées) : {{…}}
- Date & responsable : {{…}}
