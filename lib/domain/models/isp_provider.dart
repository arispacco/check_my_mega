import 'package:flutter/material.dart';

/// Represents a telecommunications Internet Service Provider (FAI) in Cameroon.
enum IspProvider {
  camtel(
    id: 'CAMTEL',
    displayName: 'Camtel / Blue',
    shortName: 'Camtel',
    ussdCode: '*825#',
    brandColor: Color(0xFF0077FF),
    defaultSamplePlanName: 'Blue Horaire 1Go (3h)',
    defaultPromisedMb: 1024,
    defaultPriceXaf: 100,
    defaultValidityHours: 3,
    sampleSms:
        'Cher client, votre souscription au forfait Blue Horaire 1Go (100 FCFA) valable 3h a reussi. Consultez votre solde au *825#.',
  ),
  mtnCm(
    id: 'MTN_CM',
    displayName: 'MTN Cameroon',
    shortName: 'MTN',
    ussdCode: '*123#',
    brandColor: Color(0xFFFFCC00),
    defaultSamplePlanName: 'Mobile Surf / Wanda 1Go',
    defaultPromisedMb: 1024,
    defaultPriceXaf: 500,
    defaultValidityHours: 24,
    sampleSms:
        "Y'ello! Vous avez active le forfait Mobile Surf 1Go (1024Mo) a 500 FCFA valable 24h. Suivi conso: *123*10#.",
  ),
  orangeCm(
    id: 'ORANGE_CM',
    displayName: 'Orange Cameroun',
    shortName: 'Orange',
    ussdCode: '#145#',
    brandColor: Color(0xFFFF6600),
    defaultSamplePlanName: 'Forfait Internet 1Go Jour',
    defaultPromisedMb: 1024,
    defaultPriceXaf: 500,
    defaultValidityHours: 24,
    sampleSms:
        'Orange Info: Votre souscription au forfait Internet 1Go a 500 FCFA est active pour 24h. Solde au #145#.',
  ),
  yoomee(
    id: 'YOOMEE',
    displayName: 'Yoomee Mobile',
    shortName: 'Yoomee',
    ussdCode: '*126#',
    brandColor: Color(0xFFE60023),
    defaultSamplePlanName: 'Data Express 1Go',
    defaultPromisedMb: 1024,
    defaultPriceXaf: 400,
    defaultValidityHours: 24,
    sampleSms:
        'Yoomee: Votre forfait Data Express 1Go (400 FCFA) valable 24h est maintenant actif.',
  );

  const IspProvider({
    required this.id,
    required this.displayName,
    required this.shortName,
    required this.ussdCode,
    required this.brandColor,
    required this.defaultSamplePlanName,
    required this.defaultPromisedMb,
    required this.defaultPriceXaf,
    required this.defaultValidityHours,
    required this.sampleSms,
  });

  final String id;
  final String displayName;
  final String shortName;
  final String ussdCode;
  final Color brandColor;
  final String defaultSamplePlanName;
  final double defaultPromisedMb;
  final int defaultPriceXaf;
  final int defaultValidityHours;
  final String sampleSms;

  static IspProvider fromIdOrText(String value) {
    final normalized = value.toUpperCase();
    if (normalized.contains('62405') ||
        normalized.contains('CAMTEL') ||
        normalized.contains('BLUE')) {
      return IspProvider.camtel;
    }
    if (normalized.contains('62401') ||
        normalized.contains('MTN') ||
        normalized.contains("Y'ELLO")) {
      return IspProvider.mtnCm;
    }
    if (normalized.contains('62402') || normalized.contains('ORANGE')) {
      return IspProvider.orangeCm;
    }
    if (normalized.contains('YOOMEE')) {
      return IspProvider.yoomee;
    }
    return IspProvider.camtel;
  }
}
