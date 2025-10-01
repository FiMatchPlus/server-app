### 1. 목적
제공된 JSON 분석 데이터를 기반으로, PMPT(Post Modern Portfolio Theory) 및 백테스팅 관점에서 포트폴리오의 성능과 최적 비중을 평가하는 상세 분석 리포트를 생성한다. 리포트는 비전문가도 쉽게 이해할 수 있도록 명확하고 실행 가능한 투자 인사이트를 제공하는 데 중점을 둔다.

### 2. 데이터 입력
{{portfolioData}}

### 3. 출력 JSON 스키마 및 내용 생성 지침

출력은 반드시 다음 JSON 스키마를 따르며, 각 필드는 상세하고 명확한 인사이트를 담아야 합니다.

| JSON 필드명 | 원본 데이터/요구사항 | 내용 생성 지침 (직관성 및 종목 분석 강화) |
| :--- | :--- | :--- |
| `report_title` | 고정값: "포트폴리오 최적화 제안서" | 날짜를 포함하여 최종 제목을 생성합니다. |
| `executive_summary` | (3.1 요약) | **[핵심]** 고객의 목표에 따라 Min-Var 또는 Max-Sharpe 중 하나를 추천하고, 그 이유를 **하락장 방어력(Max Drawdown, CVaR)**과 **리스크 조정 수익 효율(Sharpe Ratio, Sortino Ratio)** 관점에서 비유를 들어 설명합니다. 두 전략의 비중을 모두 포함합니다. |
| `current_optimal_analysis.insight_core_contributor` | (3.2 비중 해석) | 가장 비중이 높은 종목(종목코드2 등)을 **"포트폴리오의 엔진"** 또는 "수익률의 핵심 동력"과 같은 직관적인 용어로 설명하고, 해당 종목의 **기대수익률 및 변동성**을 언급하며 그 중요도를 해석합니다. |
| `current_optimal_analysis.insight_risk_diversification` | (3.2 비중 해석) | Min-Var와 Max-Sharpe 간의 비중 차이를 **"변동성 보험"**이나 "위험 회피용 안전망"과 같은 용어로 설명하며, 비중 차이가 발생한 종목의 **포트폴리오 상관관계**가 낮아 분산 효과를 극대화했음을 설명합니다. |
| `backtesting_verification.insight_sharpe` | (3.3 샤프 비율 비교) | 샤프 비율을 **"투자 효율 점수"**로, **소르티노 비율(Sortino Ratio)**을 **"하방 위험 대비 효율 점수"**로 정의합니다. 두 지표를 함께 비교하여, Min-Var가 MPT 및 PMPT 관점 모두에서 장기적으로 더 효율적이었던 이유를 설명합니다. |
| `backtesting_verification.insight_drawdown` | (3.3 리스크 비교) | 최대 낙폭(Max Drawdown)을 **"최악의 경우 손실 방어선"**으로 정의하고, **하방 표준편차(Downside Deviation)**와 **Calmar Ratio**를 활용하여 Min-Var가 하락장 방어에 얼마나 더 효과적이었는지 정교하게 강조합니다. |
| `user_portfolio_diagnosis.comparison_insight` | (3.5 핵심 비교 1) | 사용자의 '투자 효율 점수'(Sharpe/Sortino 비율)가 최적 포트폴리오 대비 낮은 경우, "현재 포트폴리오가 리스크 대비 충분한 보상을 받지 못하고 있다"는 점을 강조하고 **사용자의 종목별 리스크 기여도(stock_risk_contribution)**를 언급하여 비효율성의 근거를 제시합니다. |
| `user_portfolio_diagnosis.stability_diagnosis` | (3.5 핵심 비교 2) | 사용자의 최대 낙폭이 최적 포트폴리오보다 큰 경우, "고객님의 포트폴리오는 최악의 시장 상황에서 **추가적인 손실 위험**을 감수하고 있다"고 직관적으로 제시합니다. **VaR과 CVaR** 값을 활용하여 이 하방 위험을 금액(또는 비율)으로 환산하여 설명하고, **안정성 개선의 필요성**을 강조합니다. |
| `user_portfolio_diagnosis.improvement_recommendation.action` | (3.5 핵심 비교 3) | **[가장 중요: 종목별 분석]** 제안 전략(Min-Var 또는 Max-Sharpe)의 비중을 기준으로, **현재 비중과 제안 비중의 구체적인 차이**를 언급합니다. **종목별 상세 데이터(기대수익률, 상관관계)**를 근거로 사용하여 왜 해당 종목의 비중을 조정해야 하는지 **비유와 함께** 상세하게 설명합니다. (예: "종목코드1은 60% → 30%로 감소해야 합니다. 이 종목은 변동성(0.22) 대비 포트폴리오 상관관계(0.6)가 높아 위험 분산 효과가 낮습니다.") |

### 4. 최종 JSON 스키마

```json
{
  "report_title": "최적 포트폴리오 전략 및 진단 보고서",
  "report_date": "YYYY-MM-DD",
  
  "executive_summary": {
    "recommended_strategy_action": "Min-Variance", 
    "recommendation_reason": "...",
    "summary_comparison": {
      "min_variance": {
        "sharpe_ratio": 0.33,
        "max_drawdown": -0.22,
        "current_weights": {
				  "005930": 0.45, 
				  "000660": 0.35,
				  "035420": 0.20 
					// 합계: 1.00이 되어야 함.
				}
      },
      "max_sharpe": {
	      "sharpe_ratio": 0.32,
			  "max_drawdown": -0.25,
			  "current_weights": {
				   "005930": 0.50,
				   "000660": 0.30,
				   "035420": 0.20
  }
      }
    }
  },
  "current_optimal_analysis": {
    "analysis_period": "최근 3년 롤링 윈도우",
    "weights_table": [
      {"code": "005930", "name": "삼성전자", "min_var_weight": 0.45, "max_sharpe_weight": 0.50},
      {"code": "000660", "name": "SK하이닉스", "min_var_weight": 0.35, "max_sharpe_weight": 0.30},
      {"code": "035420", "name": "NAVER", "min_var_weight": 0.20, "max_sharpe_weight": 0.20}
    ],
    // LLM이 생성할 인사이트 텍스트가 들어갈 필드
    "insight_core_contributor": "...", 
    "insight_risk_diversification": "..." 
  },

  "backtesting_verification": {
    "analysis_basis": "백테스팅 기간 전체 (Rolling Window) 평균 성과 지표",
    "performance_table": [
      {"strategy": "Min-Variance", "expected_return": 0.085, "sharpe_ratio": 0.33, "max_drawdown": -0.22},
      {"strategy": "Max-Sharpe", "expected_return": 0.092, "sharpe_ratio": 0.32, "max_drawdown": -0.25}
    ],
    // LLM이 생성할 인사이트 텍스트가 들어갈 필드
    "insight_sharpe": "...", 
    "insight_drawdown": "..."
  },

  "user_portfolio_diagnosis": {
    // 사용자가 입력한 비중으로 계산된 현재 성과 지표
    "user_data_assumed": { 
        "expected_return": 0.078,
        "std_deviation": 0.17,
        "sharpe_ratio": 0.28,
        "max_drawdown": -0.28
    },
    // LLM이 생성할 인사이트 텍스트가 들어갈 필드
    "comparison_insight": "...",
    "stability_diagnosis": "...",
    "improvement_recommendation": {
      "strategy": "리스크-수익 효율 개선",
      "action": "..." // 구체적인 행동 지침
    }
  },
  "analysis_overview": {
    // 분석 환경 정보
    "methodology": "3년 롤링 윈도우 최적화 및 백테스팅 방식을 적용하여 결과의 신뢰도를 확보하였습니다.",
    "benchmark": "KOSPI",
    "risk_free_rate_used": 0.03
  }
}
```
