package com.assignment.multimediaqa.repository;

import com.assignment.multimediaqa.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    @Query("select distinct asset from MediaAsset asset left join fetch asset.segments")
    List<MediaAsset> findAllWithSegments();

    @Query("select distinct asset from MediaAsset asset left join fetch asset.segments where asset.id = :id")
    Optional<MediaAsset> findByIdWithSegments(Long id);

    @Query("""
            select distinct asset
            from MediaAsset asset
            left join fetch asset.segments
            where lower(asset.originalFilename) = lower(:filename)
            order by asset.createdAt desc
            """)
    List<MediaAsset> findByOriginalFilenameWithSegments(String filename);
}
