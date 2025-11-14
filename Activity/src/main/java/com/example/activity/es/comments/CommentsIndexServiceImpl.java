package com.example.activity.es.comments;

import com.example.activity.pojo.CommentESSave;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

@Service
public class CommentsIndexServiceImpl implements CommentsIndexService {

    @Resource
    private CommentsEs commentsEs;
    

    @Override
    public void indexAfterCommit(CommentESSave comment) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    commentsEs.save(comment);
                }
            });
        } else {
            commentsEs.save(comment);
        }
    }

    @Override
    public void deleteAfterCommit(Integer id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    commentsEs.deleteById(id);
                }
            });
        } else {
            commentsEs.deleteById(id);
        }
    }

    @Override
    public List<CommentESSave> findByActivityId(Integer activityId) {
        return commentsEs.findByActivityId(activityId);
    }

    // 新增实现：按ID读取 ES 记录

    @Override
    public CommentESSave findById(Integer id) {
        if (id == null) return null;
        return commentsEs.findById(id).orElse(null);
    }


}
