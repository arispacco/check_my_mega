# Cahier des Charges Fonctionnel et Technique (CDCF)
## Projet : **CheckMyMega**
**Slogan :** *« 1 Go acheté = 1 Go consommé ? Vérifiez ce que votre FAI vous livre réellement. »*  
**Contexte :** Session Live Coding — **Hacktoberfest** × **Google Gemma 4**  
**Stack retenue :** **Flutter (Dart 3) + Android Natif Kotlin (`NetworkStatsManager` / `TrafficStats`)**  
**Moteur IA :** **Gemma 4** en double mode (**Ollama Local** `gemma4:e4b` / `gemma4:12b` + **Google AI Studio API**)  
**Cible géographique initiale :** Cameroun (MTN Cameroon, Orange Cameroun, Camtel / Blue) — Extensible à toute l'Afrique francophone.

---

## 1. Contexte et Justification du Projet

### 1.1. Le constat terrain au Cameroun
Ces derniers mois au Cameroun, une frustration généralisée touche les consommateurs de données mobiles : **la vitesse d'épuisement anormale des forfaits internet** chez certains Fournisseurs d'Accès à Internet (FAI).

> [!IMPORTANT]
> **Cas concret observé :**
> - **Forfait A (Camtel)** : `100 FCFA` pour `1 Go` (validité 3h).
> - **Forfait B (MTN Cameroon)** : `500 FCFA` pour `1 Go`.
> - **Forfait C (Orange Cameroun)** : `500 FCFA` pour `1 Go`.
> 
> **Observation empirique :** Pour une charge de travail strictement identique, les forfaits de `1 Go` chez MTN et Orange s'épuisent nettement plus vite que le forfait `1 Go` de Camtel.

### 1.2. Les hypothèses techniques à vérifier par l'application
Pour qu'un outil d'audit soit crédible, scientifique et inattaquable, il ne doit pas seulement accuser : il doit **mesurer au niveau du noyau/système d'exploitation (OS)** et isoler la cause exacte de la disparition des données :

1. **Écart réel de livraison (Sous-quota FAI)** : Le système de facturation de l'opérateur (OCS/PCRF) coupe le forfait alors que l'interface réseau du téléphone n'a reçu/transmis que 600 Mo ou 750 Mo au lieu de 1 024 Mio.
2. **Arrondi de facturation par session (Billing Increment Rounding)** : L'opérateur arrondit chaque micro-session ou palier (ex. par tranche de 100 Ko ou 1 Mo) lors des coupures radio 4G, gonflant artificiellement le compteur côté FAI par rapport aux octets réellement reçus côté OS.
3. **Confusion Commerciale Décimal vs Binaire (`Go` vs `Gio`)** : 1 Go commercial ($10^9 = 1\,000\,000\,000$ octets) représente $\approx 953,67\text{ Mio}$ dans le système binaire ($2^{30} = 1\,073\,741\,824$ octets), soit un écart natif de **4,6 % à 7,3 %**.
4. **Fuites en arrière-plan (Background Data Leaks) liées au débit** : Un réseau 4G+ à fort débit (burst) déclenche des mises à jour silencieuses ou un pré-chargement vidéo en 1080p en arrière-plan sans que l'utilisateur s'en rende compte.

---

## 2. Objectifs du Projet

### 2.1. Objectif Principal
Fournir une application **Open Source (Hacktoberfest)**, transparente et assistée par **Gemma 4**, permettant à tout utilisateur de :
1. Enregistrer un forfait internet dès son activation (1 Go, 500 Mo, etc.).
2. Mesurer au niveau système (octets `Rx` reçus + `Tx` envoyés sur l'interface cellulaire) la consommation réelle pendant toute la durée de vie du forfait.
3. Ventiler cette consommation application par application pour exclure toute fuite cachée du téléphone.
4. Calculer le **Score de Vérité du Forfait (%)** au moment précis où le FAI déclare le forfait épuisé.

### 2.2. Objectifs Spécifiques (Hacktoberfest & Gemma 4)
- **Zéro consommation parasite pendant le test** : L'application (et son IA embarquée ou locale) ne doit consommer **aucun mégaoctet** du forfait qu'elle est en train d'auditer.
- **Exploitation de Gemma 4 de bout en bout** :
  - *En amont / Sur l'appareil (Gemma 4 E2B / E4B ou API)* : Extraction automatique des paramètres du forfait à partir des SMS opérateurs ou de captures d'écran USSD (Vision/OCR + JSON Function Calling).
  - *En aval (Raisonnement Gemma 4)* : Analyse intelligente du journal réseau, détection des anomalies de facturation et génération d'un rapport d'audit explicatif (en Français, Anglais, ou Camfranglais/Pidgin).
- **Observatoire Citoyen Open-Source** : Permettre aux contributeurs du Hacktoberfest d'enrichir les règles de détection des FAI (MTN, Orange, Camtel, Yoomee) et de publier un baromètre comparatif.

---

## 3. Périmètre Fonctionnel (User Stories & Fonctionnalités)

### 3.1. Module 1 : Déclaration & Détection du Forfait (Ingestion IA)
| ID | Fonctionnalité | Description | Priorité |
| :--- | :--- | :--- | :--- |
| **F1.1** | **Saisie manuelle rapide** | Choisir le FAI (MTN, Orange, Camtel/Blue), le volume promis (ex: 1 Go), le coût (ex: 100 FCFA, 500 FCFA) et la durée (ex: 3h, 24h, 30j). | **P0 (MVP Live)** |
| **F1.2** | **Parsing intelligent SMS / USSD avec Gemma 4** | Coller le SMS de confirmation du FAI ou importer une capture d'écran du popup USSD. **Gemma 4** extrait automatiquement : `opérateur`, `volume_mo`, `prix_fcfa`, `validité`. | **P0 (MVP Live)** |
| **F1.3** | **Détection automatique SMS de fin de forfait** | Détecter le SMS d'épuisement (*"Votre forfait internet est épuisé"*) ou le passage du débit à 0 octet/s pour figer automatiquement le compteur d'audit. | **P1** |

### 3.2. Module 2 : Sonde Réseau Système (OS Network Metering)
| ID | Fonctionnalité | Description | Priorité |
| :--- | :--- | :--- | :--- |
| **F2.1** | **Capture du compteur matériel/OS (`Baseline`)** | Au clic sur *"Démarrer l'Audit"*, enregistrer l'index exact des octets cellulaires (`RxBytes` + `TxBytes`) depuis l'API système (`NetworkStatsManager` / `TrafficStats` sur Android ou `/proc/net/dev` / `psutil` sur PC via partage de connexion). | **P0 (MVP Live)** |
| **F2.2** | **Suivi temps réel Rx / Tx** | Afficher en direct : Octets téléchargés (Download), Octets envoyés (Upload), Total consommé en `Mo` (base 10) ET en `Mio` (base 2). | **P0 (MVP Live)** |
| **F2.3** | **Audit par Application (Anti-biais)** | Lister exactement quelles applications (YouTube, WhatsApp, Chrome, Services OS) ont consommé des données pendant la session d'audit pour prouver que l'utilisateur fait « le même travail ». | **P0 (MVP Live)** |
| **F2.4** | **Détecteur d'arrondi de session (Burst/Idle)** | Compter le nombre de micro-sessions et de coupures réseau pour estimer si le FAI pénalise l'utilisateur par des arrondis de facturation. | **P2** |

### 3.3. Module 3 : Verdict, Comparateur FAI & Diagnostic Gemma 4
| ID | Fonctionnalité | Description | Priorité |
| :--- | :--- | :--- | :--- |
| **F3.1** | **Calcul du « Taux de Vérité »** | Ratio : $\text{Taux de Vérité} = \frac{\text{Volume Réel Mesuré par l'OS}}{\text{Volume Promis par le FAI}} \times 100$. | **P0 (MVP Live)** |
| **F3.2** | **Comparateur Multi-FAI ("Même Travail")** | Tableau comparatif côte à côte : Session Camtel (100 FCFA / 1 Go) vs Session MTN (500 FCFA / 1 Go) vs Session Orange (500 FCFA / 1 Go) avec coût réel au Mo livré (`FCFA / Mo réel`). | **P0 (MVP Live)** |
| **F3.3** | **Rapport d'Expert généré par Gemma 4** | Gemma 4 analyse les métriques de la session et rédige un diagnostic clair : identifie si l'écart vient du FAI (octets manquants au niveau de l'interface réseau) ou d'une application gourmande en arrière-plan, et génère une fiche partageable (image/PDF/texte pour X, WhatsApp ou réclamation ART). | **P0 (MVP Live)** |

---

## 4. Spécifications Non-Fonctionnelles et Contraintes

1. **Neutralité Réseau de l'Application (Crucial)** :
   - Pendant qu'un audit de forfait est actif, l'application fonctionne en mode **100 % Hors-Ligne (Offline-First)**.
   - Si Gemma 4 est appelé pendant le test, il tourne soit **en local** (Gemma 4 E2B/E4B via LiteRT / Ollama local), soit uniquement **une fois l'audit terminé** pour ne pas fausser le compteur d'un seul octet.
2. **Permissions Système Minimales & Transparentes** :
   - Sur Android : `PACKAGE_USAGE_STATS` (Accès aux données d'utilisation pour lire `NetworkStatsManager`), `READ_PHONE_STATE` (optionnel pour identifier la carte SIM active Dual-SIM MTN/Orange/Camtel).
   - Sur Desktop / Web (Mode Passerelle / Hotspot ou Simulateur Live) : Lecture directe des interfaces réseau de l'OS (`psutil` / `netstat` / API Web) + injection de journaux Android (`adb shell dumpsys netstats`).
3. **Conformité Open Source (Hacktoberfest)** :
   - Licence : **Apache 2.0** (alignée avec la licence officielle de Gemma 4).
   - Fichiers obligatoires : `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`, templates d'Issues étiquetés `hacktoberfest` et `good first issue`.

---

## 5. Pourquoi Gemma 4 est au cœur du projet ?

| Modèle Gemma 4 | Rôle dans **DataVeritas CM** | Pourquoi ce choix ? |
| :--- | :--- | :--- |
| **Gemma 4 E2B / E4B** *(Edge / Mobile / Local)* | **Parseur SMS/USSD & Vision OCR** + **Diagnostic hors-ligne** | Tourne localement (0 Mo de data consommée !), supporte nativement le **Function Calling** (sortie JSON stricte) et la **Vision** (lecture d'une capture d'écran USSD `*123#` ou `*825#`). |
| **Gemma 4 12B / 26B A4B** *(Ollama / AI Studio)* | **Analyste Réseau & Générateur de Rapports Citoyens** | Mode *Thinking/Reasoning* pour croiser les courbes de consommation `Rx/Tx`, calculer l'impact des arrondis par paquets et rédiger un rapport d'audit détaillé en français ou en camfranglais. |

---

## 6. Critères d'Acceptation pour la Session Live (MVP)

Pour que ta session Live Hacktoberfest soit un succès spectaculaire devant ton audience :
- [ ] **Démo 1 (Ingestion IA)** : Coller un vrai SMS de souscription Camtel, MTN et Orange (ou une capture d'écran) $\rightarrow$ **Gemma 4** extrait instantanément le JSON structuré du forfait.
- [ ] **Démo 2 (Mesure Système Réelle)** : Lancer un compteur de session lié aux statistiques réseau réelles du système, consommer des données sur une tâche reproductible ("Même travail"), et visualiser la jauge **Promis vs Réel OS**.
- [ ] **Démo 3 (Comparaison Camtel vs MTN vs Orange)** : Afficher le comparatif des 3 forfaits de 1 Go (`100 FCFA` vs `500 FCFA`) montrant le **Vrai Prix du Go reçu** et le pourcentage d'écart.
- [ ] **Démo 4 (Verdict Gemma 4)** : Générer en direct avec Gemma 4 le **Certificat d'Audit Réseau** prêt à être partagé par la communauté.
