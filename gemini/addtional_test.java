@Test
@DisplayName("should log parsing errors and continue migration")
void shouldLogParsingErrorsAndContinue() throws ExecutionException, InterruptedException {
    // Given
    HintDao validHint = HintTestDataGenerator.createHintDaoWithId("validId");
    Document validDoc = HintTestDataGenerator.createDocumentFromHintDao(validHint);
    Document invalidDoc = new Document("_id", "invalidId");

    when(mongoTemplate.find(any(Query.class), eq(Document.class), anyString()))
        .thenReturn(List.of(invalidDoc, validDoc))
        .thenReturn(Collections.emptyList());
    when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(2L);
    when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");

    // Mock converter to throw exception for invalid document, succeed for valid
    when(mongoTemplate.getConverter().read(eq(HintDao.class), any(Document.class)))
        .thenThrow(new RuntimeException("Parse error"))
        .thenReturn(validHint);

    when(hintRepository.existsByMongoUUID("validId")).thenReturn(false);

    // When
    migrationService.startMigration(testJob).get();

    // Then
    verify(hintRepository, times(1)).save(any(HintEntity.class));
    ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
    verify(migrationErrorRepo).save(errorCaptor.capture());
    assertThat(errorCaptor.getValue().getMongoUUID()).isEqualTo("invalidId");
    assertThat(errorCaptor.getValue().getMessage()).contains("Failed to parse HintDao");
}