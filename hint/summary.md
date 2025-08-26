# Hint Service

Der Hint Service ist Bestandteil der ELISA (ELPA in der SDA) Microservice-Architektur, wird zudem aber auch vom ELPA-System verwendet.
Er empfängt sowohl über die synchrone REST-Api als auch über die asynchrone Kafka-API Hints (Hinweise) und speichert diese in einer mongoDB.
Über die REST-Api können zudem alle Hints oder konkrete Hints anhand der ID abgefragt werden.