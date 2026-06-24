# Plan directeur d'Exécution — Promotion Production du Hotfix patrimoine/bien

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Approuvé par le PO — exécution séquentielle non démarrée** |
| Type | Hotfix vers Production |
| Artefact Staging prouvé | `sha-0adc4941` |
| Production actuelle | `1.1.0` — `sha-05424aa3` |
| Décision de cadrage | Parcours validé en **GO sous réserve** |

## Principe d'exécution obligatoire

Aucune étape ci-dessous ne peut être exécutée directement. Avant chaque étape, un **plan
d'étape daté** doit être produit. Il précise :

- objectif et périmètre ;
- prérequis et preuves d'entrée ;
- commandes ou mutations prévues ;
- risques et critères d'arrêt ;
- rollback ;
- preuves de sortie ;
- décision explicite autorisant l'étape.

La validation d'une étape n'autorise pas automatiquement la suivante.

## Étape 1 — Préparer le candidat Production

Plan détaillé produit : `docs/cgpa/09-production/plan-etape-1-preparation-candidat-hotfix.md`.
Statut : **exécutée le 2026-06-24 — candidat recevable**.

Plan requis : identification du tag immuable disponible, contrôle CI/CodeQL/SonarQube, périmètre
du Hotfix, changelog et release notes, comparaison avec le tag Production courant.

Sortie : dossier candidat complet, sans changement d'état Production.

Décision : **candidat recevable**. Dossier : `docs/cgpa/09-production/release-candidate-v1.1.1-hotfix.md`.

## Étape 2 — Statuer le Gate Production accéléré

Plan détaillé produit : `docs/cgpa/09-production/plan-etape-2-gate-production-hotfix.md`.
Statut : **exécutée le 2026-06-24 — GO sous réserve acceptée, `PRODUCTION_READY`**.

Plan requis : checklist Gate Production, preuves Staging, `STG-ISOL-01`, validations PO/Release
Manager, risques ouverts et stratégie de rollback.

Sortie : **GO sous réserve acceptée** ; `PRODUCTION_READY` atteint. Aucun accès Production, backup ou déploiement. Décision : `docs/cgpa/09-production/gate-production-v1.1.1-hotfix-decision.md`.

## Étape 3 — Préflight Production et sauvegarde

Plan détaillé produit : `docs/cgpa/09-production/plan-etape-3-preflight-backup-production.md`.
Statut : **exécutée le 2026-06-24 — préflight PASS**.

Plan requis : contrôle de l'hôte, espace disque, santé des services, inventaire des versions,
sauvegarde PostgreSQL, vérification `pg_restore --list`, disponibilité du tag précédent
`sha-05424aa3` et critères de rollback.

Sortie : **préflight PASS** ; backup `loyertracker-20260624-140441.dump` vérifié. Rapport : `docs/cgpa/09-production/preflight-backup-v1.1.1-report.md`.

## Étape 4 — Déployer le Hotfix

Plan détaillé produit : `docs/cgpa/09-production/plan-etape-4-deploiement-hotfix.md`.
Statut : **exécutée le 2026-06-24 — déploiement technique PASS**.

Plan requis : commandes Docker Compose ciblées, services à recréer, ordre d'exécution, fenêtre
d'intervention, surveillance et seuils d'arrêt.

Sortie : API/Web `sha-0adc4941` déployés, contrôles techniques PASS, `PRODUCTION_DEPLOYED` non atteint. Rapport : `docs/cgpa/09-production/deploiement-technique-v1.1.1-report.md`.

## Étape 5 — Valider ou rollback

Plan détaillé produit : `docs/cgpa/09-production/plan-etape-5-validation-finale-rollback.md`.
Statut : **exécutée le 2026-06-24 — PASS, `PRODUCTION_DEPLOYED`**.

Plan requis : healthchecks, Flyway V1→V14 inchangé, smoke Production, parcours critique
création/édition de bien, contrôles Keycloak, observabilité et nettoyage de test.

Sortie : **succès — `PRODUCTION_DEPLOYED`**. Smoke 47/0, nettoyage complet, tag persistant. Rapport : `docs/cgpa/09-production/validation-finale-v1.1.1-report.md`.

## Réserves et hors périmètre

- `RSV-STG-01` reste ouverte jusqu'à une nouvelle preuve live Staging.
- Le bug distinct d'injection CORS dans les fichiers Compose est hors de ce Hotfix.
- Aucun démarrage du Sprint 3 Patrimoine n'est autorisé.

## Clôture

Parcours Hotfix `1.1.1` clôturé en `PRODUCTION_DEPLOYED`.
