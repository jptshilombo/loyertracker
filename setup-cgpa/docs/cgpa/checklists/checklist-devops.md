# Checklist DevOps / CI-CD

> À dérouler au **Gate 6 (DevSecOps)** et à maintenir tout au long du projet.

## 1. Gestion de versions
- [ ] Tout est versionné (code, infra, configs, docs)
- [ ] Stratégie de branches définie
- [ ] Convention de commits / PR avec revue

## 2. Intégration continue (CI)
- [ ] Build automatisé à chaque push/PR
- [ ] Tests unitaires & d'intégration exécutés en CI
- [ ] Quality gate (couverture, lint) bloquant
- [ ] Scans de sécurité (SAST/SCA/secrets) intégrés

## 3. Packaging
- [ ] Build d'images Docker reproductible
- [ ] Images versionnées et publiées dans un registry
- [ ] Configuration externalisée (12-factor)

## 4. Déploiement continu (CD)
- [ ] Environnements distincts `dev` / `staging` / `prod`
- [ ] Déploiement automatisé vers `staging`
- [ ] Déploiement `prod` contrôlé (approbation)
- [ ] Stratégie de déploiement définie (rolling/blue-green/canary)

## 5. Infrastructure
- [ ] Infrastructure as Code (si applicable)
- [ ] Secrets gérés hors dépôt
- [ ] Sauvegardes automatisées

## 6. Observabilité
- [ ] Logs centralisés
- [ ] Métriques & dashboards
- [ ] Alerting configuré

## 7. Réversibilité
- [ ] Plan de rollback documenté
- [ ] Procédure de restauration testée
