@Service
public class HintService2 {
    private static final Logger log = LoggerFactory.getLogger(HintService.class);
    
    private final HintRepository hintRepository;
    private final HintMapper hintMapper;
    private final MeterRegistry meterRegistry;
    
    public HintService(
        HintRepository hintRepository,
        HintMapper hintMapper,
        MeterRegistry meterRegistry
    ) {
        this.hintRepository = hintRepository;
        this.hintMapper = hintMapper;
        this.meterRegistry = meterRegistry;
    }
    
    public List<HintDto> getHints(Map<HintParams, Object> queryParams) {
        List<HintEntity> hintEntities = new ArrayList<>();
        
        if (queryParams.containsKey(HintParams.PROCESS_ID) && queryParams.size() == 1) {
            hintEntities.addAll(
                this.hintRepository.findAllByProcessId(
                    queryParams.get(HintParams.PROCESS_ID).toString()
                )
            );
        } else if (queryParams.containsKey(HintParams.PROCESS_ID) && 
                   queryParams.containsKey(HintParams.HINT_SOURCE_PREFIX) && 
                   queryParams.size() == 2) {
            String processId = queryParams.get(HintParams.PROCESS_ID).toString();
            String hintSourcePrefix = queryParams.get(HintParams.HINT_SOURCE_PREFIX).toString();
            hintEntities.addAll(
                this.hintRepository.findAllByProcessIdAndHintSourceStartingWith(
                    processId, hintSourcePrefix
                )
            );
        } else {
            Specification<HintEntity> hintEntitySpecification = 
                HintSpecifications.fromQuery(queryParams);
            hintEntities.addAll(
                this.hintRepository.findAll(hintEntitySpecification)
            );
        }
        
        return hintEntities.stream().map(hintMapper::entityToDto).toList();
    }
    
    @Timed(value = "hints.save.duration", description = "Time to save hints")
    public void saveHints(List<HintDto> hints) {
        hints.forEach(hint -> log.debug("Saving hint: {}", hint));
        
        final List<HintEntity> hintEntities = hints.stream()
            .map(hintMapper::dtoToEntity)
            .toList();
        
        hintRepository.saveAll(hintEntities);
        
        // Record metrics
        recordHintMetrics(hints);
    }
    
    public Optional<HintDto> getHintById(Long id) {
        return hintRepository.findById(id).map(hintMapper::entityToDto);
    }
    
    private void recordHintMetrics(List<HintDto> hints) {
        // Total counter
        meterRegistry.counter("hints.created.total")
            .increment(hints.size());
        
        // By category
        Map<HintDto.Category, Long> byCategory = hints.stream()
            .collect(Collectors.groupingBy(
                HintDto::hintCategory,
                Collectors.counting()
            ));
        
        byCategory.forEach((category, count) -> {
            meterRegistry.counter(
                "hints.created",
                "category", category.name()
            ).increment(count);
        });
        
        // By source (optional)
        Map<String, Long> bySource = hints.stream()
            .collect(Collectors.groupingBy(
                HintDto::hintSource,
                Collectors.counting()
            ));
        
        bySource.forEach((source, count) -> {
            meterRegistry.counter(
                "hints.created",
                "source", source
            ).increment(count);
        });
    }
}