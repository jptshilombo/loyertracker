# Rapport de validation runtime R6 — Keycloak OIDC/PKCE et Admin API

Date : 2026-06-07  
Cadre : CGPA v3.0.1 — reserve Gate 6 R6  
Perimetre : stack Docker Compose locale `loyertracker`, point d'entree `https://localhost`.

## Objectif

Valider en runtime :

* le realm Keycloak `loyertracker` ;
* le flux Authorization Code + PKCE S256 du client SPA `loyertracker-spa` ;
* l'appel API protege avec JWT bailleur ;
* le parcours invitation / acceptation gestionnaire utilisant l'Admin API Keycloak.

## Etat runtime avant validation

La stack etait deja active, mais l'image API initiale datait du 2026-06-06 et ne contenait que la migration `V1__init_schema.sql`. Elle ne refletait pas le code courant S02.

Action runtime executee : reconstruction et redemarrage du service `api` uniquement via Docker Compose, sans modification de fichier du depot.

Etat apres rebuild :

* `api` healthy ;
* JAR API reconstruit le 2026-06-07 ;
* 6 repositories JPA detectes ;
* migrations Flyway V1 a V4 validees et appliquees ;
* controleurs S02 et `InvitationController` presents dans le JAR.

## Resultats

| Controle | Resultat | Preuve synthese |
| -------- | -------- | --------------- |
| OIDC discovery | OK | `issuer=https://localhost/auth/realms/loyertracker`, status 200 |
| API health | OK | `/api/actuator/health` status 200, `UP` |
| Client Keycloak `loyertracker-api` inspectable | OK | client trouve dans le realm |
| Configuration client `loyertracker-api` | KO | `bearerOnly=true`, `serviceAccountsEnabled=false` |
| `client_credentials` Admin API | KO | token endpoint status 401 `unauthorized_client` |
| Flux OIDC Authorization Code + PKCE S256 | OK | JWT bailleur obtenu, `aud=loyertracker-api`, role `BAILLEUR`, `expires_in=300` |
| Inscription bailleur API | OK | status 409 attendu car compte de test deja inscrit |
| API protegee `/api/biens` | OK | status 200, liste retournee |
| Emission invitation bailleur | OK | `POST /api/invitations` status 201, token genere |
| Acceptation invitation / Admin API gestionnaire | KO | `POST /api/invitations/{token}/acceptation` status 401 |

## Incoherences detectees

* `.env` contient `KEYCLOAK_API_CLIENT_SECRET`, mais `docker-compose.yml` ne transmet pas `KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_ADMIN_BASE_URL` ni `KEYCLOAK_REALM` au conteneur `api`.
* Le realm versionne declare `loyertracker-api` comme resource server `bearerOnly`, sans service account. Cette configuration est incompatible avec le flux `client_credentials` attendu par `KeycloakGestionnaireIdentityProvider`.
* Nginx sert encore `infra/nginx/html` comme placeholder. La validation effectuee couvre le flow PKCE/API en runtime, mais pas une navigation Angular build complet servie par Nginx.

## Verdict R6

R6 est partiellement validee, non cloturee.

* GO pour OIDC discovery, PKCE S256, JWT bailleur et appel API protege.
* NO GO pour l'Admin API gestionnaire tant que le client confidentiel/service account et l'injection des variables runtime API ne sont pas corriges.

## Actions correctives recommandees

1. Creer ou convertir un client confidentiel Keycloak dedie a l'Admin API, avec `serviceAccountsEnabled=true`.
2. Attribuer au service account les droits minimaux necessaires a la creation/recherche utilisateur et a l'assignation du role `GESTIONNAIRE`.
3. Injecter les variables runtime dans `api` : `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`.
4. Reexecuter R6 sur le parcours complet invitation -> acceptation -> creation/rattachement gestionnaire.
5. Prevoir une validation separee du build Angular servi par Nginx si l'objectif est de couvrir la SPA packagenee, pas seulement le protocole OIDC/PKCE.
