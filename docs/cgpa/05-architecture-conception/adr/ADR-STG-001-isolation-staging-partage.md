# ADR-STG-001 — Isolation obligatoire des stacks Docker sur environnement staging partagé

> Alias historique CGPA v5.4 conservé. Depuis CGPA v5.4.1, le chemin canonique est
> `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`.

| Champ | Valeur |
|-------|--------|
| Code de décision | **D-STG-01** (associées : D-STG-02 à D-STG-05) |
| Statut | **Acceptée — GO le 2026-06-24**, dans le cadre de la migration CGPA v5.4 |
| Date | 2026-06-24 |
| Phase | 07 — Développement (gouvernance transverse Staging) |
| Référentiel | `setup-cgpa/docs/cgpa/CGPA-v5.4.md` §3-4 |
| Documents liés | `docs/cgpa/migration/audit-initial-v5.4.md`, `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md`, `docs/cgpa/checklists/stg-isol-01-checklist.md`, `docs/cgpa/workflows/staging-isolation-workflow.md`, `docs/cgpa/environment-promotion-model.md` |

## Contexte

L'environnement Staging de LoyerTracker (`docker-compose.staging.yml`) tourne sur `ai-test-server`
(IP privée `172.31.11.102`), un hôte **mutualisé** hébergeant également d'autres périmètres
(« loyerpro », outils labo — Security Group `innovtech-ai-lab-sg`) derrière un reverse proxy
partagé (nginx-proxy-manager occupant les ports 80/443 de l'hôte). Ce choix d'hébergement
mutualisé a été acté dès le lot Production Readiness 4b (2026-06-14, `docs/staging-state.md` §1)
pour des raisons de coût et de proportionnalité (profil PME), sans qu'une politique formelle
d'isolation inter-projets n'ait été explicitement rédigée à l'époque. CGPA v5.4 introduit cette
exigence de façon transverse pour tout projet gouverné par le cadre.

## Problème

1. Un déploiement, un rollback ou une opération de nettoyage Docker exécuté pour LoyerTracker sur
   un hôte mutualisé peut, en l'absence de garde-fous explicites, affecter les conteneurs,
   réseaux, volumes ou ports d'un autre projet hébergé sur le même serveur (ex. arrêt accidentel
   de conteneurs, suppression de volumes, conflit de ports avec un service tiers).
2. Aucune décision d'architecture documentée n'expliquait jusqu'ici **pourquoi** et **comment**
   l'isolation entre projets est garantie sur cet hôte — seule une description opérationnelle
   existait (`docs/staging-state.md` §1, `runbook-exploitation.md` §2).
3. CGPA v5.4 rend le contrôle `STG-ISOL-01` bloquant au Gate Staging : une décision d'architecture
   explicite est nécessaire pour fonder ce contrôle et écarter les approches qui sembleraient
   intuitivement « plus sûres » mais sont en réalité incompatibles avec un hôte mutualisé.

## Décision

**Isoler chaque stack Docker par projet, sans jamais agir sur le périmètre global de l'hôte.**

Principes retenus :

1. **Nom de projet Compose explicite et unique** (`name: loyertracker-staging`,
   `docker-compose.staging.yml` ligne 1) — distinct de tout autre projet hébergé. Toutes les
   ressources créées (conteneurs, réseau, volume) sont namespacées par Docker Compose à partir de
   ce nom (D-STG-02).
2. **Réseau et volumes dédiés**, déclarés dans le fichier Compose du projet uniquement
   (`loyertracker-net`, `postgres-data`), jamais de réseau ou volume externe partagé non
   inventorié.
3. **Variables d'environnement et secrets dédiés** (`.env` propre au projet, hors dépôt), jamais
   partagés avec un autre projet de l'hôte.
4. **Aucune commande Docker à portée globale** dans les procédures de déploiement, de rollback ou
   d'exploitation : toute commande cible explicitement `-f docker-compose.staging.yml` (qui
   résout le nom de projet `loyertracker-staging`). Les commandes telles que
   `docker stop $(docker ps -q)`, `docker compose down` sans fichier ni projet explicite, ou
   `docker system prune -a` sont **interdites** dans tout script, runbook ou pipeline LoyerTracker.
5. **Ports applicatifs non publiés directement** : seuls les points d'entrée nécessaires
   (`nginx`) publient un port hôte, paramétrable pour éviter tout conflit ; les autres services
   (`api`, `keycloak`, `postgres`) restent strictement internes au réseau du projet.
6. **Publication par nom DNS via le reverse proxy mutualisé** (nginx-proxy-manager), jamais par
   exposition durable et directe d'un port applicatif dédié sur l'hôte.
7. **Ressources intentionnellement partagées inventoriées** : le reverse proxy mutualisé et
   l'hôte lui-même sont les seules ressources partagées ; elles sont recensées avec leur
   propriétaire et leurs conditions d'usage (`docs/staging-state.md` §11).
8. **Contrôle `STG-ISOL-01` bloquant** au Gate Staging (D-STG-03) : toute exception nécessite une
   décision explicite, motivée, datée et tracée du Release Manager (D-STG-05).

Ce contrôle est exécuté via le workflow `staging-isolation-workflow.md` et la checklist
`stg-isol-01-checklist.md`, et statué formellement par `gate-stg-isol-01-decision.md`.

## Alternatives rejetées

| Alternative | Raison du rejet |
|--------------|------------------|
| **Arrêter tous les conteneurs présents sur le serveur avant chaque déploiement.** | **Rejetée explicitement.** Cette approche supprime le problème d'isolation en supprimant la mutualisation elle-même : elle interromprait systématiquement les autres projets hébergés (« loyerpro », outils labo) à chaque déploiement LoyerTracker, à l'encontre du principe de gouvernance CGPA v5.4 (« le déploiement d'un projet ne doit jamais arrêter… les conteneurs… d'un autre projet »). Elle est en outre non scalable (le risque d'incident croît avec chaque projet additionnel sur l'hôte) et masque le vrai problème (absence de ciblage précis) plutôt que de le résoudre. |
| Provisionner un hôte Staging dédié à LoyerTracker (fin de la mutualisation) | Coût disproportionné pour un MVP en profil PME (ENV-01) ; l'isolation logique par projet Compose atteint le même objectif de sécurité sans le coût d'un hôte dédié supplémentaire. Reste une option future si le risque mutualisé devient inacceptable. |
| Réseau Docker externe unique partagé entre tous les projets de l'hôte, avec règles de pare-feu applicatif | Complexifie la gestion des accès sans bénéfice : un réseau dédié par projet Compose atteint nativement l'isolation réseau requise sans configuration supplémentaire. |
| Exposer chaque projet sur un port hôte dédié sans reverse proxy mutualisé | Multiplie les ports publics à gérer et à sécuriser individuellement (TLS, accès) au lieu de centraliser via un reverse proxy déjà éprouvé (nginx-proxy-manager) ; contraire à la recommandation v5.4 §3 (publication par nom DNS). |

## Conséquences

- ✅ Aucune modification d'infrastructure requise : l'isolation namespace/réseau/volume/ports/
  reverse proxy est **déjà en place** depuis le lot Production Readiness 4b (2026-06-14) ; cette
  ADR formalise une pratique existante et la rend auditable.
- ✅ Le Gate Staging dispose désormais d'un contrôle bloquant explicite (`STG-ISOL-01`) au lieu
  d'une isolation reposant uniquement sur la discipline opérationnelle non documentée.
- ✅ Aucune rupture du modèle d'environnements ENV-01 (Dev → Test → Staging → Production) : la
  Production reste un hôte dédié, hors périmètre de mutualisation.
- ⚠️ La confirmation *live* du contrôle (inspection directe de l'hôte au moment d'un déploiement,
  en présence d'autres projets) reste à exécuter formellement (RSV-STG-01), la preuve actuelle
  étant documentaire et historique (aucun incident sur 6 redéploiements 2026-06-14 → 2026-06-24).
- ⚠️ Aucune preuve automatisée (script ou étape CI) ne produit aujourd'hui `STG-ISOL-01` : la
  vérification reste manuelle/documentaire à ce stade ; une automatisation pourra être évaluée
  sans remettre en cause la présente décision.

## Risques

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Dérive opérationnelle : une commande Docker globale est introduite involontairement dans un futur script ou runbook | Moyen | Revue explicite anti-commande-globale intégrée au workflow `staging-isolation-workflow.md` (étape 4) et à la checklist `stg-isol-01-checklist.md` |
| Nouvelle ressource partagée introduite sans inventaire (ex. base de données mutualisée, registre commun) | Moyen | D-STG-04 : toute ressource partagée doit être inventoriée avec propriétaire et conditions d'usage avant usage |
| Confirmation live `STG-ISOL-01` jamais exécutée si aucun nouveau déploiement Staging n'intervient | Faible | RSV-STG-01 suivie au registre des risques (`docs/project-state.md`), à lever au prochain déploiement réel quel qu'il soit |
