package com.sahil.assetportal.service;

import com.sahil.assetportal.entity.Asset;
import com.sahil.assetportal.repository.AssetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public Asset createAsset(Asset asset) {
        return assetRepository.save(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found with id: " + id));
    }

    public Asset updateAsset(Long id, Asset updatedAsset) {
        Asset existingAsset = getAssetById(id);

        existingAsset.setAssetCode(updatedAsset.getAssetCode());
        existingAsset.setAssetType(updatedAsset.getAssetType());
        existingAsset.setName(updatedAsset.getName());
        existingAsset.setBrand(updatedAsset.getBrand());
        existingAsset.setModel(updatedAsset.getModel());
        existingAsset.setSerialNumber(updatedAsset.getSerialNumber());
        existingAsset.setStatus(updatedAsset.getStatus());
        existingAsset.setLocation(updatedAsset.getLocation());

        return assetRepository.save(existingAsset);
    }

    public void deleteAsset(Long id) {
        Asset existingAsset = getAssetById(id);
        assetRepository.delete(existingAsset);
    }
}