# FiMatchPlus Backend

Spring Boot 기반의 투자 포트폴리오 관리 및 백테스트 서비스 백엔드 API

## 프로젝트 구조

```
src/main/java/com/fimatchplus/backend/
├── BackendApplication.java
│
├── ai/                                    # AI 기반 분석 및 챗봇
│   ├── controller/
│   │   ├── BacktestReportController.java
│   │   └── ChatbotController.java
│   ├── dto/
│   │   ├── BacktestReportRequest.java
│   │   └── BacktestReportResponse.java
│   └── service/
│       ├── AIService.java
│       ├── BacktestReportService.java
│       ├── CategoryChatbotService.java
│       ├── ChatbotAIService.java
│       ├── ChatbotPromptConstants.java
│       ├── PortfolioReportService.java
│       ├── PromptTemplateService.java
│       └── ReportAIService.java
│
├── backtest/                              # 백테스트 실행 및 관리
│   ├── controller/
│   │   └── BacktestController.java
│   ├── domain/
│   │   ├── ActionType.java
│   │   ├── Backtest.java
│   │   ├── BenchmarkIndex.java
│   │   ├── BenchmarkPrice.java
│   │   ├── ExecutionLog.java
│   │   ├── HoldingSnapshot.java
│   │   ├── PortfolioSnapshot.java
│   │   ├── ResultStatus.java
│   │   └── RuleCategory.java
│   ├── dto/
│   │   ├── BacktestCallbackResponse.java
│   │   ├── BacktestDetailResponse.java
│   │   ├── BacktestErrorResponse.java
│   │   ├── BacktestExecutionRequest.java
│   │   ├── BacktestExecutionResponse.java
│   │   ├── BacktestMetaData.java
│   │   ├── BacktestMetrics.java
│   │   ├── BacktestResponse.java
│   │   ├── BacktestResponseMapper.java
│   │   ├── BacktestServerErrorResponse.java
│   │   ├── BacktestStartResponse.java
│   │   ├── BacktestStatus.java
│   │   ├── CreateBacktestRequest.java
│   │   ├── CreateBacktestResult.java
│   │   ├── DailyReturn.java
│   │   ├── MissingStockData.java
│   │   └── UpdateBacktestRequest.java
│   ├── event/
│   │   ├── BacktestFailureEvent.java
│   │   └── BacktestSuccessEvent.java
│   ├── exception/
│   │   └── BacktestExecutionException.java
│   ├── repository/
│   │   ├── BacktestRepository.java
│   │   ├── BacktestRuleRepository.java
│   │   ├── BenchmarkPriceRepository.java
│   │   ├── ExecutionLogJdbcRepository.java
│   │   ├── SnapshotRepository.java
│   │   └── SnapshotRepositoryImpl.java
│   ├── service/
│   │   ├── BacktestDataPersistenceService.java
│   │   ├── BacktestEngineClient.java
│   │   ├── BacktestExecutionService.java
│   │   ├── BacktestQueryService.java
│   │   ├── BacktestRuleDocument.java
│   │   ├── BacktestService.java
│   │   └── BacktestStatusManager.java
│   └── util/
│       └── ThresholdValueNormalizer.java
│
├── common/                                # 공통 유틸리티 및 설정
│   ├── config/
│   │   ├── AsyncConfig.java
│   │   ├── WebClientConfig.java
│   │   └── WebConfig.java
│   ├── dto/
│   │   └── ApiResponse.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── ResourceNotFoundException.java
│   ├── service/
│   │   └── BacktestJobMappingService.java
│   └── util/
│       └── DateTimeUtil.java
│
├── portfolio/                             # 포트폴리오 관리 및 분석
│   ├── controller/
│   │   ├── PortfolioAnalysisController.java
│   │   └── PortfolioController.java
│   ├── domain/
│   │   ├── BenchmarkIndex.java
│   │   ├── Holding.java
│   │   ├── Portfolio.java
│   │   └── Rules.java
│   ├── dto/
│   │   ├── CreatePortfolioRequest.java
│   │   ├── CreatePortfolioResult.java
│   │   ├── PortfolioAnalysisDetailResponse.java
│   │   ├── PortfolioAnalysisRequest.java
│   │   ├── PortfolioAnalysisResponse.java
│   │   ├── PortfolioAnalysisStartResponse.java
│   │   ├── PortfolioDetailResponse.java
│   │   ├── PortfolioInsightReport.java
│   │   ├── PortfolioListResponse.java
│   │   ├── PortfolioLongResponse.java
│   │   ├── PortfolioShortResponse.java
│   │   ├── PortfolioStatusResponse.java
│   │   ├── PortfolioSummaryResponse.java
│   │   └── UpdatePortfolioRequest.java
│   ├── event/
│   │   ├── PortfolioAnalysisFailureEvent.java
│   │   ├── PortfolioAnalysisSuccessEvent.java
│   │   └── PortfolioCreatedEvent.java
│   ├── repository/
│   │   ├── PortfolioRepository.java
│   │   ├── PortfolioRepositoryImpl.java
│   │   └── RulesRepository.java
│   └── service/
│       ├── BenchmarkDeterminerService.java
│       ├── PortfolioAnalysisDetailService.java
│       ├── PortfolioAnalysisEngineClient.java
│       ├── PortfolioAnalysisService.java
│       ├── PortfolioCalculator.java
│       ├── PortfolioCommandService.java
│       └── PortfolioQueryService.java
│
├── product/                               # 투자 상품 관리
│   ├── controller/
│   │   └── ProductController.java
│   ├── domain/
│   │   ├── Product.java
│   │   ├── ProductHolding.java
│   │   ├── RiskLevel.java
│   │   └── StringArrayConverter.java
│   ├── dto/
│   │   ├── HoldingInfo.java
│   │   ├── ProductDetailResponse.java
│   │   ├── ProductListResponse.java
│   │   └── ProductSummary.java
│   ├── repository/
│   │   ├── ProductHoldingRepository.java
│   │   └── ProductRepository.java
│   └── service/
│       └── ProductService.java
│
├── stock/                                 # 주식 정보 조회
│   ├── controller/
│   │   └── StockController.java
│   ├── converter/
│   │   └── BooleanToYNConverter.java
│   ├── domain/
│   │   ├── PriceChangeSign.java
│   │   ├── Stock.java
│   │   ├── StockPrice.java
│   │   └── StockType.java
│   ├── dto/
│   │   ├── StockDetailResponse.java
│   │   ├── StockPriceResponse.java
│   │   └── StockSearchResponse.java
│   ├── repository/
│   │   ├── StockPriceRepository.java
│   │   └── StockRepository.java
│   └── service/
│       ├── KisMultiPriceResponse.java
│       ├── KisPriceClient.java
│       ├── KisQuoteResponse.java
│       ├── KisTokenResponse.java
│       ├── KisTokenService.java
│       └── StockService.java
│
└── user/                                  # 사용자 관리
    ├── domain/
    │   ├── Channel.java
    │   ├── Gender.java
    │   └── User.java
    └── repository/
        └── UserRepository.java
```

## API 엔드포인트

### 1. AI 분석 및 챗봇

#### 백테스트 리포트
- `POST /reports/backtest` - 백테스트 결과 기반 AI 분석 레포트 생성
- `POST /reports/backtest/{backtestId}/regenerate` - 백테스트 레포트 재생성

#### 챗봇
- `GET /chat/{category}` - 카테고리별 챗봇 질문 (손절/익절/포트폴리오)
- `GET /chat/categories` - 지원하는 카테고리 목록 조회

---

### 2. 백테스트

- `POST /backtests/portfolio/{portfolioId}` - 백테스트 생성
- `GET /backtests/portfolio/{portfolioId}` - 포트폴리오별 백테스트 조회
- `GET /backtests/{backtestId}` - 백테스트 상세 정보 조회
- `GET /backtests/{backtestId}/metadata` - 백테스트 메타데이터 조회
- `GET /backtests/portfolios/{portfolioId}/status` - 포트폴리오별 백테스트 상태 조회
- `POST /backtests/{backtestId}/execute` - 백테스트 실행
- `POST /backtests/callback` - 백테스트 엔진 콜백 수신
- `PUT /backtests/{backtestId}/portfolio/{portfolioId}` - 백테스트 수정
- `DELETE /backtests/{backtestId}/portfolio/{portfolioId}` - 백테스트 삭제

---

### 3. 포트폴리오

#### 포트폴리오 관리
- `POST /portfolios` - 포트폴리오 생성
- `GET /portfolios` - 사용자 포트폴리오 목록 조회
- `GET /portfolios/summary` - 포트폴리오 통합 합계 정보 조회
- `GET /portfolios/main` - 대표 포트폴리오 요약 정보 조회
- `GET /portfolios/{portfolioId}` - 포트폴리오 기본 정보 조회
- `GET /portfolios/{portfolioId}/long` - 포트폴리오 상세 정보 조회
- `GET /portfolios/{portfolioId}/analysis` - 포트폴리오 분석 결과 요약 조회
- `GET /portfolios/{portfolioId}/detail` - 포트폴리오 분석 상세 정보 조회
- `PUT /portfolios/{portfolioId}` - 포트폴리오 수정
- `DELETE /portfolios/{portfolioId}` - 포트폴리오 삭제

#### 포트폴리오 분석
- `POST /portfolio-analysis/callback` - 포트폴리오 분석 엔진 콜백 수신
- `POST /portfolio-analysis/{portfolioId}/start` - 포트폴리오 최적화 수동 실행
- `POST /portfolio-analysis/{portfolioId}/report` - 포트폴리오 분석 리포트 수동 생성
- `GET /portfolio-analysis/{portfolioId}/status` - 포트폴리오 분석 상태 조회

---

### 4. 투자 상품

- `GET /products` - 상품 목록 조회
- `GET /products/{productId}` - 상품 상세 조회

---

### 5. 주식

- `GET /stocks` - 여러 종목의 현재가 정보 조회
- `GET /stocks/detail` - 단일 종목의 상세 정보 조회
- `GET /stocks/chart` - 특정 종목의 차트 데이터 조회
- `GET /stocks/search` - 종목 이름 또는 코드로 검색
- `GET /stocks/now` - 단일 종목 현재가 조회
- `GET /stocks/multi` - 여러 종목의 실시간 현재가 조회

---

## 기술 스택

- **Framework**: Spring Boot
- **Build Tool**: Gradle
- **AI Integration**: Spring AI (OpenAI)
- **Database**: MySQL, MongoDB
- **External APIs**: KIS (한국투자증권) API

