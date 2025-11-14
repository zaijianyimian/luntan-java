package com.example.activity.es.activity;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import com.example.activity.pojo.ActivityESSave;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class ActivityIndexService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityIndexService.class);
    public final static int DEFAULT_PAGE_SIZE = 2;

    @Resource
    private ActivityES activityES;

    @Resource
    private ElasticsearchClient client;

    @Resource
    private AsyncElasticsearchService asyncElasticsearchService;

    @Transactional
    public void indexAfterCommit(ActivityESSave activities) {
        asyncElasticsearchService.indexAsync(activities);
    }

    @Transactional
    public void deleteAfterCommit(Integer id) {
        asyncElasticsearchService.deleteAsync(id);
    }

    public List<ActivityESSave> searchByKeyword(String keyword) {
        try {
            var searchResponse = client.search(s -> s
                            .index("activity-all")
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .fields("title", "description", "tags")
                                            .query(keyword)
                                            .fuzziness("AUTO")
                                            .type(TextQueryType.BestFields)
                                            .operator(Operator.Or)
                                    )
                            ),
                    ActivityESSave.class);

            return searchResponse.hits().hits().stream()
                    .map(h -> h.source())
                    .toList();

        } catch (Exception e) {
            logger.error("Elasticsearch 查询异常", e);
            return Collections.emptyList();
        }
    }

    public Page<ActivityESSave> findAllBy(Integer pageNum) {
        Pageable pageable = PageRequest.of(pageNum - 1, DEFAULT_PAGE_SIZE);
        return activityES.findAllBy(pageable);
    }

    public Page<ActivityESSave> findByCategoryId(Integer categoryId, Integer pageNum) {
        Pageable pageable = PageRequest.of(pageNum - 1, DEFAULT_PAGE_SIZE);
        return activityES.findByCategoryId(categoryId, pageable);
    }

}
