# Rapport final — Validation Production du Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Décision | **PASS — `PRODUCTION_DEPLOYED`** |
| Version | `1.1.1` |
| Commit | `0adc4941f854304a3f7412b04294615b05403707` |
| Tag | `sha-0adc4941` |
| Rollback | `sha-05424aa3` |
| Backup | `loyertracker-20260624-140441.dump` |

## Validation runtime

- Smoke Production : **47 PASS / 0 FAIL**.
- Flyway : 14 migrations, schéma à jour.
- API connectée sous `loyertracker_api`, NOSUPERUSER/NOBYPASSRLS.
- JWT Keycloak, Admin API, paiements, honoraires, alertes, audit et isolation cross-tenant
  validés.
- Création du patrimoine, du bien et du bail : 201.
- Affectation patrimoine et honoraires : conformes.
- Second bailleur : inscription 201, patrimoine principal créé automatiquement, aucune fuite
  cross-tenant.

## Preuve frontend

- Image Web identique au digest validé par navigateur en Staging.
- Racine Production et tous les assets JavaScript/CSS retournent HTTP 200.
- Aucun changement de redirect URI, CORS, hostname, Security Group ou port n'a été nécessaire.
- La preuve navigateur Staging demeure la preuve fonctionnelle du composant, complétée par le
  smoke Production sur le même artefact.

## Nettoyage

Run : `1782311743`.

Éléments supprimés de manière ciblée :

- 1 patrimoine ;
- 1 bien ;
- 1 bail ;
- 1 affectation ;
- 10 paiements ;
- 10 honoraires ;
- 7 alertes ;
- 2 écritures d'audit du run ;
- 1 invitation et 1 gestionnaire ;
- 1 second bailleur et son patrimoine principal ;
- 2 utilisateurs Keycloak éphémères.

Les compteurs des tables métier sont revenus exactement à l'état de référence. Aucun marqueur
du run ne subsiste.

État Keycloak final :

- `bailleur-test@test.local` : désactivé ;
- `loyertracker-spa.directAccessGrantsEnabled` : `false` ;
- utilisateurs éphémères : absents.

## Persistance du tag

- `.env` sauvegardé dans `.env.pre-1.1.1-20260624-143838`, permissions `600`.
- `LOYERTRACKER_TAG=sha-0adc4941` présent exactement une fois.
- `docker compose config --images` résout les images candidates.
- Aucun restart ou recreate n'a été effectué lors de la persistance.

## État final

| Contrôle | Résultat |
|---|---|
| API/Web | `sha-0adc4941`, digests conformes |
| Services applicatifs | Healthy, restart count 0 |
| Flyway | V1→V14 |
| API Actuator | UP |
| Assets Web | HTTP 200 |
| Prometheus | 5/5 cibles `up` |
| Alertes | Aucune |
| Données de smoke | Nettoyées |
| Backup | Vérifié |
| Rollback | Disponible |

## Risques maintenus

- `RSV-STG-01` reste ouverte jusqu'au prochain mouvement Staging réel.
- Le défaut distinct d'injection des variables CORS dans les fichiers Compose reste hors
  périmètre et doit être traité dans un lot dédié.
- Les fichiers `.env` de sauvegarde hors dépôt doivent faire l'objet d'une revue d'hygiène
  d'exploitation sans suppression automatique.

## Décision finale

**Chief Delivery Officer : GO — `PRODUCTION_DEPLOYED`.**

Le Hotfix `1.1.1` est déployé et validé sur
`https://loyertracker.loyerpro.org`.
