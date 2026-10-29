# Guide de Contribution — Hacktoberfest × CheckMyMega

Merci de contribuer à **CheckMyMega** ! Ce projet citoyen open-source vise à apporter de la transparence sur la facturation des forfaits internet mobiles grâce aux compteurs système (`NetworkStatsManager` / `TrafficStats`) et à **Google Gemma 4**.

---

## 🌟 Idées de Contributions (`good first issue` / `hacktoberfest`)

1. **Nouveaux formats de SMS / USSD Opérateurs (`lib/domain/models/isp_provider.dart`)** :
   - Ajouter les variantes de SMS de souscription et d'épuisement pour **MTN Wanda Net**, **Orange Pulse / Max it**, **Camtel Blue**, **Yoomee**.
   - Étendre aux opérateurs d'Afrique francophone (Côte d'Ivoire, Sénégal, RDC, Bénin, Togo, Gabon).
2. **Prompts & Évaluations Gemma 4 (`lib/data/services/gemma_service.dart`)** :
   - Ajouter le support multimodal (OCR sur capture d'écran de popup USSD avec Gemma 4 Vision).
   - Ajouter le support multilingue (Français, English, Pidgin, Camfranglais).
3. **Export Image / PDF du Certificat d'Audit (`lib/ui/features/audit/views/gemma_report_tab.dart`)** :
   - Générer une carte visuelle PNG directement partageable sur WhatsApp et X.
4. **Tests Unitaires & Widget Tests (`test/`)** :
   - Ajouter des cas de tests pour les arrondis de facturation par session et les calculs `Mo` vs `Mio`.

---

## 📏 Règles de Commits Atomiques (Conventional Commits)

Chaque commit doit respecter le principe de **responsabilité unique** (pas de « ET » / « AND » mélangeant deux sujets) :

- `feat(isp): add Orange Cameroun Max-it SMS templates`
- `fix(android): handle null subscriberId on Android 14+`
- `test(calculator): add binary MiB conversion unit tests`
- `docs(readme): add adb reverse instructions for local Ollama`

### Workflow Standard
1. Forkez le dépôt et créez votre branche (`git checkout -b feat/ma-fonctionnalite`).
2. Vérifiez l'analyse statique et les tests :
   ```bash
   dart analyze lib test
   flutter test
   ```
3. Ouvrez votre Pull Request !
