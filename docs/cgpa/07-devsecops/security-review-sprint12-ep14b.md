# Revue sécurité dédiée — Sprint 12 EP-14b

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Candidat | `75646d8` / `sha-75646d8f` |
| Périmètre | API et page publiques de vérification, téléchargement PDF, métriques et rate-limit |
| Verdict | **PASS — aucune réserve bloquante** |

## Contrôles

| Contrôle | Résultat | Preuve |
|---|---|---|
| Authentification publique ciblée | PASS | `permitAll` limité aux deux routes GET ; toutes les autres routes restent authentifiées |
| Capability HMAC | PASS | Token non stocké, HMAC-SHA256 lié à `id.version.content_hash`, comparaison en temps constant |
| Absence d'oracle | PASS | Identifiant inconnu, token absent/forgé/décalé : même verdict `INVALIDE`; téléchargement invalide : 404 uniforme |
| Non-fuite K2 | PASS | DTO reconstruit depuis le contenu certifié ; mode de paiement, garantie retenue, email et identifiants internes absents, test négatif dédié |
| Intégrité PDF | PASS | Recalcul SHA-256 avant envoi ; test d'altération en base et validation live du hash |
| Fonctions privilégiées | PASS | `SECURITY DEFINER`, `search_path=public`, propriétaire batch, `EXECUTE` seulement pour le rôle API ; rôle API `NOSUPERUSER/NOBYPASSRLS` |
| Journal RGPD | PASS | Aucun IP/user-agent/token ; SQL constant et paramètres liés ; identifiant inconnu journalisé avec `quittance_id IS NULL` |
| Frontend | PASS | Route sans `authGuard` uniquement pour `/verify/receipt/:id`, Bearer exclu uniquement de `/api/public/`, rendu Angular sans HTML de confiance contourné, `noindex/nofollow` |
| Nginx | PASS | Rate-limit par IP, CSP, HSTS, `X-Frame-Options: DENY`; log JSON basé sur `$uri` sans query string ni Referer |
| CI sécurité | PASS | Gitleaks, OWASP Dependency-Check, Trivy dépendances et images API/Web, CodeQL Java/TypeScript : verts sur le merge `75646d8` |

## Validation live Staging

- Rate-limit : 21 réponses 200 puis 69 réponses 429 sur une rafale de 90 requêtes.
- Métriques présentes : `quittance_verifications_total`, `quittance_telechargements_total`,
  `quittance_qr_invalides_total`.
- Quittance réelle `QT-2026-000001` : verdict `VALIDE`; PDF téléchargé dont le SHA-256 égale
  `pdf_hash`. Le token a été calculé en mémoire et n'a été ni affiché ni persisté.
- Smoke complet : 62 PASS / 0 FAIL ; échafaudage Keycloak révoqué à la fin.

## Observation non bloquante

`Referrer-Policy: strict-origin-when-cross-origin` est conforme au socle actuel : aucune URL
complète n'est envoyée à un domaine tiers. `no-referrer` constituerait un durcissement futur, sans
fuite démontrée dans l'implémentation actuelle. Aucun correctif n'est requis pour le Gate.

## Décision

Le risque « nouvelle surface publique » du plan EP-14 est traité. La revue sécurité autorise le
Gate Staging ; elle n'autorise aucune promotion Production.
