# ADR-STG-001 — Isolation obligatoire des stacks Docker sur Staging partagé

| Champ | Valeur |
|---|---|
| Statut | Acceptée |
| Date | 2026-06-24 |
| Référentiel | CGPA v5.4.1 |
| Décisions | D-STG-01 à D-STG-05 |
| Gate | `STG-ISOL-01` |

## Contexte

L'environnement Staging `ai-test-server` héberge plusieurs projets. Un déploiement LoyerTracker
ne doit arrêter, supprimer ou modifier aucune ressource appartenant à un autre projet.

## Décision

Chaque projet dispose de sa propre stack Docker Compose, de son namespace, de ses réseaux,
volumes, variables et secrets. Les ports publiés sont sans collision et le reverse proxy route
par nom DNS. Les pipelines, déploiements et rollbacks ciblent exclusivement LoyerTracker.

Les commandes Docker à portée globale ou destructrice sont interdites. Un résultat
`STG-ISOL-01 = FAIL` impose `NO GO` au Gate Staging.

## Alternative rejetée

L'arrêt global des conteneurs avant déploiement est rejeté : il interrompt les autres projets,
masque l'absence d'isolation et n'est pas reproductible en Production.

## Preuves LoyerTracker

- Projet Compose : `loyertracker-staging`.
- Réseau et volume namespacés par Compose.
- Services internes non publiés ; ports web paramétrables.
- Reverse proxy mutualisé vers `loyertracker.staging.loyerpro.org`.
- Déploiement et rollback ciblés par `docker-compose.staging.yml`.
- Checklist canonique : `docs/cgpa/checklists/stg-isol-01-checklist.md`.

## Compatibilité

Le document historique détaillé
`docs/cgpa/05-architecture-conception/adr/ADR-STG-001-isolation-staging-partage.md` est conservé
comme alias v5.4. Le présent fichier est le chemin canonique CGPA v5.4.1.
