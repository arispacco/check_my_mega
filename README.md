# CheckMyMega CM 🇨🇲 × Google Gemma 4

> **« 1 Go acheté = 1 Go consommé ? Vérifiez ce que votre FAI vous livre réellement au niveau du système d'exploitation. »**

[![Hacktoberfest](https://img.shields.io/badge/Hacktoberfest-2026-FF7518?style=for-the-badge)](https://hacktoberfest.com)
[![Gemma 4](https://img.shields.io/badge/AI-Google%20Gemma%204-10B981?style=for-the-badge)](https://ai.google.dev/gemma)
[![Flutter](https://img.shields.io/badge/Flutter-3.44+-02569B?style=for-the-badge)](https://flutter.dev)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)

---

## 🎯 Le Problème Résolu

Au Cameroun (et dans plusieurs pays d'Afrique francophone), les utilisateurs constatent un épuisement anormalement rapide des forfaits internet mobiles selon l'opérateur :
- **Forfait Camtel / Blue** : `100 FCFA` pour `1 Go` (validité 3h)
- **Forfait MTN Cameroon** : `500 FCFA` pour `1 Go` (validité 24h)
- **Forfait Orange Cameroun** : `500 FCFA` pour `1 Go` (validité 24h)

Pour **exactement le même travail**, certains forfaits de `1 Go` s'épuisent alors que la carte réseau du téléphone (`NetworkStatsManager` / `TrafficStats`) n'a reçu que **620 à 660 Mo**.

**CheckMyMega** est une application open-source construite pendant le **Hacktoberfest** avec **Google Gemma 4** qui interroge directement les compteurs matériels de l'OS Android (`RxBytes` + `TxBytes` et consommation par application `UID`) pour calculer :
1. **Le Volume Réel Reçu par l'OS** (en `Mo` décimaux base 10 et `Mio` binaires base 2).
2. **Le Taux de Vérité du Forfait (%)** et les **Mégas Fantômes** perdus.
3. **Le Vrai Coût en FCFA par Go réellement livré**.
4. **La Preuve d'Absence de Fuite Interne** grâce à la ventilation par application (YouTube, Chrome, WhatsApp, OS).

---

## 🧠 Pourquoi Google Gemma 4 ? (Zéro Mo consommé pendant le test)

Un auditeur de forfait internet ne doit **pas consommer les mégas** du forfait qu'il est en train de surveiller :
- **Mode Local Ollama (`gemma4:e4b` / `gemma4:12b`)** : Exécution 100 % locale sans consommer un seul octet mobile.
- **Extraction intelligente des SMS & USSD (`*825#`, `*123#`, `#145#`)** : Gemma 4 extrait automatiquement `{ isp, planName, promisedMb, priceXaf, validityHours }`.
- **Certificat d'Audit Réseau** : Gemma 4 rédige à la fois une analyse technique rigoureuse et une synthèse citoyenne partageable sur WhatsApp et X.

---

## 🚀 Démarrage Rapide

### 1. Prérequis
- **Flutter SDK** `>= 3.44.0` (Dart `>= 3.12.0`)
- *(Optionnel)* **Ollama** installé localement avec **Gemma 4** :
  ```bash
  ollama pull gemma4:e4b
  ollama serve
  ```

### 2. Lancer l'application
```bash
cd check_my_mega
flutter pub get
flutter run
```

> **Astuce Téléphone Android + Ollama Local via USB :**
> Branchez votre téléphone Android en USB avec le débogage USB actif et redirigez le port Ollama :
> ```bash
> adb reverse tcp:11434 tcp:11434
> ```
> Votre téléphone utilisera ainsi **Gemma 4** sur votre PC via le câble USB en consommant **0 octet** de votre forfait mobile !

---

## 🤝 Contribuer pour le Hacktoberfest

Consultez [CONTRIBUTING.md](CONTRIBUTING.md) pour découvrir les issues `good first issue` et `hacktoberfest` (ajout d'opérateurs africains, nouveaux formats de SMS/USSD, export d'image du certificat, tests unitaires).
