# Registre agents CGPA v5.4 — LoyerTracker

| Rôle | Statut | Fiche |
|------|--------|-------|
| Chief Delivery Officer | Actif | `chief-delivery-officer.md` |
| Governance Officer | Actif | `governance-officer.md` |
| Enterprise Architect | Actif | `enterprise-architect.md` |
| DevSecOps Lead | Actif | `devsecops-lead.md` |
| Release Manager | Actif | `release-manager.md` |

## Règle v5.3 (conservée)

Les décisions Staging et Production doivent mobiliser au minimum Governance Officer, DevSecOps Lead et Release Manager. Le Chief Delivery Officer porte la décision finale GO / GO sous réserve / NO GO.

## Règle v5.4

Toute décision de promotion Staging sur l'environnement mutualisé `ai-test-server` mobilise en
outre l'avis du DevSecOps Lead (isolation Docker), du Release Manager (validation `STG-ISOL-01`)
et de l'Enterprise Architect (stratégie d'isolation et ressources partagées). Le Governance
Officer audite la conformité documentaire de ce contrôle. Référence :
`docs/cgpa/workflows/staging-isolation-workflow.md`.
