package com.stockone19.backend.portfolio.repository;

import com.stockone19.backend.portfolio.domain.Rules;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RulesRepository extends MongoRepository<Rules, String> {
}
