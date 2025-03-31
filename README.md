
# Confidentialité des données sur les offres SaaS Kafka ou Pulsar

Guillaume DUFRENE, Staff Engineer @ AXA France
- Devoxx 2024
- Cloud Nord 2024
- Forum InCYBER Lille 2025

## A propos

Ce repository contient l'ensemble du code présenté à devoxx en avril 2024 et à cloud nord en octobre 2024.


__Attention__  
Ce code est fourni à but purement pédagogique.
Il ne doit pas être considéré comme une solution fiable et robuste pour des usages de "production".

## Copyright / Licence

Voir fichier [LICENSE]

## Parcourir les exemples

Des tags sont positionnés sur ce repository afin de retrouver le code
strictement nécessaire aux étapes présentées en démo.

### Hello Kafka

* tag: demo1a
  Un projet spring-kafka présentant la production et consommation basique de messages.

* tag: demo1b
  Ajout d'un chiffrement naïf et faible (AES/ECB).
  N'utilisez surtout pas ce mode de chiffrement de bloc avec AES !

### Serializer / Deserializer

* tag: demo2a
  Utilisation de Serializer / Deserializer pour déporter la technique

* tag: demo2b
  Json/Chiffrement dans les Serializer / Deserializer

### Vault Centralisé

* tag: demo3
  Ajout un mock de vault
  Utilisation d'un client Rest pour intéroger et chiffrer des "clés de session"

### Chiffrement avec Pulsar

* tag: demo4
  Surcharge de CryptoMessage pour utiliser un vault centralisé.

