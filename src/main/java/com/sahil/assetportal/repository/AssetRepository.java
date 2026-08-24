package com.sahil.assetportal.repository;

import com.sahil.assetportal.entity.Asset;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AssetRepository {

    private final List<Asset> assets = new ArrayList<>();
    private long nextId = 1;

    public Asset save(Asset asset) {
        asset.setId(nextId++);
        assets.add(asset);
        return asset;
    }

    public List<Asset> findAll() {
        return assets;
    }

    public Optional<Asset> findById(Long id) {
        return assets.stream()
                .filter(asset -> asset.getId().equals(id))
                .findFirst();
    }
}