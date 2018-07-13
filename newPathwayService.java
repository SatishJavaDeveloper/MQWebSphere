package com.redpinesignals.am.service;

import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.apache.commons.collections.map.LinkedMap;
import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.redpinesignals.am.exceptions.NotFoundException;
import com.redpinesignals.am.model.AssetEntity;
import com.redpinesignals.am.model.MapEntity;
import com.redpinesignals.am.model.PathwayExitPointEntity;
import com.redpinesignals.am.model.PathwayLineEntity;
import com.redpinesignals.am.model.PathwayObstacleEntity;
import com.redpinesignals.am.model.PathwayPointEntity;
import com.redpinesignals.am.model.PathwayZoneEntity;
import com.redpinesignals.am.model.TagEntity;
import com.redpinesignals.am.model.UserEntity;
import com.redpinesignals.am.model.UserTagRelationEntity;
import com.redpinesignals.am.model.ZoneEntity;
import com.redpinesignals.am.repository.AssetRepository;
import com.redpinesignals.am.repository.MapRepository;
import com.redpinesignals.am.repository.PathwayExitPointRepository;
import com.redpinesignals.am.repository.PathwayLineRepository;
import com.redpinesignals.am.repository.PathwayObstacleRepository;
import com.redpinesignals.am.repository.PathwayPointRepository;
import com.redpinesignals.am.repository.PathwayZoneRepository;
import com.redpinesignals.am.repository.TagRepository;
import com.redpinesignals.am.repository.UserTagRelationRepository;
import com.redpinesignals.am.repository.ZoneRepository;
import com.redpinesignals.am.util.PathwayUtils;
import com.redpinesignals.am.util.ShortestPath;
import com.redpinesignals.am.util.ShortestPathResponse;

/**
 * Created by sreedhar on 08/30/17.
 */

@Component
public class PathwayService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private ZoneRepository zoneRepository;

	@Autowired
	private UserTagRelationRepository userTagRelationRepository;

	@Autowired
	private PathwayPointRepository pathwayPointRepository;

	@Autowired
	private PathwayLineRepository pathwayLineRepository;

	@Autowired
	private MapRepository mapRepository;

	@Autowired
	private PathwayZoneRepository pathwayZoneRepository;

	@Autowired
	private PathwayObstacleRepository pathwayObstacleRepository;

	@Autowired
	private AssetRepository assetRepository;

	/*@Autowired
	private MapScaleRepository mapScaleRepository;*/

	@Autowired
	private PathwayExitPointRepository pathwayExitPointRepository;

	@Autowired
	private Environment env;

	@Autowired
	private AssetService assetService;
	/*
	 * This method is used to get shortest path between user tag and zone
	 */
	public ShortestPathResponse getShortestPathForZone(UserEntity userEntity, long zoneId) {
		ShortestPathResponse shortestPathResponse = new ShortestPathResponse();
		UserTagRelationEntity userTagRelationEntity = userTagRelationRepository.findByUserByUserId(userEntity);
		if (userTagRelationEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
			return shortestPathResponse;
		}

		ZoneEntity zone = zoneRepository.findById(zoneId);
		if (zone == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.ZONE_NOT_FOUND);
			return shortestPathResponse;
		}

		Point2D zonePoint = PathwayUtils.centerPointOfPolygonPoints(getZonePoints(zone.getPolygon()));

		TagEntity sourceTagEntity = tagRepository.findById(userTagRelationEntity.getTagId());
		TagEntity destTagEntity = new TagEntity();
		destTagEntity.setLastX(zonePoint.getX());
		destTagEntity.setLastY(zonePoint.getY());
		destTagEntity.setLastMapId(zone.getMapId());

		MapEntity sourceMapEntity = mapRepository.findById(sourceTagEntity.getLastMapId());
		if (sourceMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.SOURCE_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		MapEntity destMapEntity = mapRepository.findById(destTagEntity.getLastMapId());
		if (destMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.DEST_MAP_NOT_FOUND);
			return shortestPathResponse;
		}

		PathwayZoneEntity sourceZoneEntity = getPathwayZoneByPoint(sourceMapEntity, sourceTagEntity);
		PathwayZoneEntity destZoneEntity = getPathwayZoneByPoint(destMapEntity, destTagEntity);

		if (sourceMapEntity.getId().equals(destMapEntity.getId())) {
			return getShortestPathForSingleMap(shortestPathResponse, sourceTagEntity, destTagEntity, sourceZoneEntity,
					destZoneEntity, sourceMapEntity, destMapEntity);
		} else {
			return getShortestPathForExit(shortestPathResponse, sourceMapEntity, destMapEntity, sourceTagEntity,
					destTagEntity, sourceZoneEntity, destZoneEntity);
			
		}
	}

	/*
	 * This method is used to get shortest path between user tag and asset tag
	 */
	public ShortestPathResponse getShortestPath(UserEntity userEntity, long destAssetId) {
		ShortestPathResponse shortestPathResponse = new ShortestPathResponse();

		AssetEntity destAssetEntity = assetRepository.findById(destAssetId);
		if (destAssetEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.DEST_ASSET_NOT_FOUND);
			return shortestPathResponse;
		}
		UserTagRelationEntity userTagRelationEntity = userTagRelationRepository.findByUserByUserId(userEntity);
		if (userTagRelationEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
			return shortestPathResponse;
		}

		TagEntity sourceTagEntity = tagRepository.findById(userTagRelationEntity.getTagId());
		TagEntity destTagEntity = tagRepository.findByAssetByAssetId(destAssetEntity);

		MapEntity sourceMapEntity = mapRepository.findById(sourceTagEntity.getLastMapId());
		if (sourceMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.SOURCE_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		MapEntity destMapEntity = mapRepository.findById(destTagEntity.getLastMapId());
		if (destMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.DEST_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		PathwayZoneEntity sourceZoneEntity = getPathwayZoneByPoint(sourceMapEntity, sourceTagEntity);
		PathwayZoneEntity destZoneEntity = getPathwayZoneByPoint(destMapEntity, destTagEntity);

		if (sourceMapEntity.getId().equals(destMapEntity.getId())) {
			return getShortestPathForSingleMap(shortestPathResponse, sourceTagEntity, destTagEntity, sourceZoneEntity,
					destZoneEntity, sourceMapEntity, destMapEntity);
		} else {
			return getShortestPathForExit(shortestPathResponse, sourceMapEntity, destMapEntity, sourceTagEntity,
					destTagEntity, sourceZoneEntity, destZoneEntity);			
		}
	}

	/*
	 * This method is used to get shortest path if user and asset in single map
	 */
	private ShortestPathResponse getShortestPathForSingleMap(ShortestPathResponse shortestPathResponse,
			TagEntity sourceTagEntity, TagEntity destTagEntity, PathwayZoneEntity sourceZoneEntity,
			PathwayZoneEntity destZoneEntity, MapEntity sourceMapEntity, MapEntity destMapEntity) {
		shortestPathResponse.setDestMapFilename(destMapEntity.getMapName());
		shortestPathResponse.setDestMapHeight(destMapEntity.getHeight());
		shortestPathResponse.setDestMapId(destMapEntity.getId());
		shortestPathResponse.setDestMapWidth(destMapEntity.getWidth());
		shortestPathResponse.setSourceMapFilename(sourceMapEntity.getMapName());

		shortestPathResponse.setSourceMapHeight(sourceMapEntity.getHeight());
		shortestPathResponse.setSourceMapId(sourceMapEntity.getId());
		shortestPathResponse.setSourceMapWidth(sourceMapEntity.getWidth());
		shortestPathResponse.setSourceMapScale(sourceMapEntity.getScale());
		shortestPathResponse.setSourceMapScaleX(sourceMapEntity.getScaleX());
		shortestPathResponse.setSourceMapScaleY(sourceMapEntity.getScaleY());
		shortestPathResponse.setDestMapScale(destMapEntity.getScale());
		shortestPathResponse.setDestMapScaleX(destMapEntity.getScaleX());
		shortestPathResponse.setDestMapScaleY(destMapEntity.getScaleY());
		shortestPathResponse.setStartpoint(new Point2D.Double(sourceTagEntity.getLastX()*sourceMapEntity.getScaleX(),
				sourceTagEntity.getLastY()*sourceMapEntity.getScaleY()));
		shortestPathResponse.setEndpoint(new Point2D.Double(destTagEntity.getLastX(),destTagEntity.getLastY()));
		if (destZoneEntity == null || sourceZoneEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
			return shortestPathResponse;
		}
		logger.info("dest zone : " + destZoneEntity.getZoneName() + " source zone : " + sourceZoneEntity.getZoneName());
		List<PathwayPointEntity> sourceMapPointList = pathwayPointRepository.findByMapByMapId(sourceMapEntity);
		List<PathwayPointEntity> destMapPointList = pathwayPointRepository.findByMapByMapId(destMapEntity);
		PathwayLineEntity nearestDestPathLine = getNearestPathLine(destTagEntity,
				getPathwayLinesInZone(destMapEntity, destMapPointList, destZoneEntity));
		PathwayLineEntity nearestSourcePathLine = getNearestPathLine(sourceTagEntity,
				getPathwayLinesInZone(sourceMapEntity, sourceMapPointList, sourceZoneEntity));
		if (nearestDestPathLine != null && nearestSourcePathLine != null) {
			logger.info("nearestDestPathLine : " + nearestDestPathLine);
			logger.info("nearestSourcePathLine : " + nearestSourcePathLine);
			Point2D sourceClosestPoint = PathwayUtils.getClosestPointOnSegment(
					nearestSourcePathLine.getStartPointEntity(), nearestSourcePathLine.getEndPointEntity(),
					new Point2D.Double(sourceTagEntity.getLastX()*sourceMapEntity.getScaleX(), sourceTagEntity.getLastY()*sourceMapEntity.getScaleY()));
			
			Point2D destClosestPoint = PathwayUtils.getClosestPointOnSegment(nearestDestPathLine.getStartPointEntity(),
					nearestDestPathLine.getEndPointEntity(),
					new Point2D.Double(destTagEntity.getLastX(), destTagEntity.getLastY()));
			JSONObject shortestPath = getFinalShortestPath(nearestSourcePathLine, nearestDestPathLine, sourceMapEntity,
					destMapEntity, sourceTagEntity, destTagEntity, sourceClosestPoint, destClosestPoint);
			if (shortestPath == null) {
				shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
				shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
				return shortestPathResponse;
			}
			logger.info("shortestPath : " + shortestPath);
			populateShortestPathResponse(sourceMapEntity, shortestPathResponse, shortestPath, nearestSourcePathLine, nearestDestPathLine,
					sourceClosestPoint, destClosestPoint);
			shortestPathResponse.setStatus(PathwayUtils.Status.SUCCESS);
			shortestPathResponse.setExit(false);
			if (shortestPathResponse.getPathpoints().size() > 2) {
				poupateDirectionAndNavMsg(sourceMapEntity.getScale(), shortestPathResponse,sourceMapEntity);
			} else {
				shortestPathResponse.setDirection(PathwayUtils.Direction.STRAIGHT);
				shortestPathResponse.setNavigationMsg("Go straight to reach the destination.");

			}
			shortestPathResponse.setUom(env.getProperty("redpine.am.indoornavigation-uom"));
			int estimatedTime = (int) Math
					.round(1 / (Double.parseDouble(env.getProperty("redpine.am.indoornavigation-estimated-uom-in-min")))
							* shortestPathResponse.getDistance());
            if(shortestPathResponse.getEstimatedTimeInMins()==null)
            {
                shortestPathResponse.setEstimatedTimeInMins(estimatedTime);
            }
            if (estimatedTime == 0 && shortestPathResponse.getEstimatedTimeInMins()!=0) {
                shortestPathResponse.setEstimatedTimeInMins(1);
			} else {
				shortestPathResponse.setEstimatedTimeInMins(estimatedTime);
			}

		} else {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
			return shortestPathResponse;
		}
		return shortestPathResponse;
	}

	/*
	 * This method is used to get shortest path between user and nearest exit
	 * point if user and asset in different maps
	 */
	private ShortestPathResponse getShortestPathForExit(ShortestPathResponse shortestPathResponse,
			MapEntity sourceMapEntity, MapEntity destMapEntity, TagEntity sourceTagEntity, TagEntity destTagEntity,
			PathwayZoneEntity sourceZoneEntity, PathwayZoneEntity destZoneEntity) {
		shortestPathResponse.setDestMapFilename(destMapEntity.getMapName());
		shortestPathResponse.setDestMapHeight(destMapEntity.getHeight());
		shortestPathResponse.setDestMapId(destMapEntity.getId());
		shortestPathResponse.setDestMapWidth(destMapEntity.getWidth());
		shortestPathResponse.setSourceMapFilename(sourceMapEntity.getMapName());
		shortestPathResponse.setSourceMapHeight(sourceMapEntity.getHeight());
		shortestPathResponse.setSourceMapId(sourceMapEntity.getId());
		shortestPathResponse.setSourceMapWidth(sourceMapEntity.getWidth());
		shortestPathResponse.setSourceMapScale(sourceMapEntity.getScale());
		shortestPathResponse.setSourceMapScaleX(sourceMapEntity.getScaleX());
		shortestPathResponse.setSourceMapScaleY(sourceMapEntity.getScaleY());
		shortestPathResponse.setDestMapScale(destMapEntity.getScale());
		shortestPathResponse.setDestMapScaleX(destMapEntity.getScaleX());
		shortestPathResponse.setDestMapScaleY(destMapEntity.getScaleY());
		ShortestPathResponse shortestPath = getNearestExitShortestPath(shortestPathResponse, sourceTagEntity,
				destTagEntity, sourceZoneEntity, destZoneEntity, sourceMapEntity, destMapEntity);
		if (shortestPath.getStatus().equalsIgnoreCase(PathwayUtils.Status.FAIL)) {
			return shortestPath;
		}

		shortestPathResponse.setExit(true);
		shortestPath.setStatus(PathwayUtils.Status.SUCCESS);
		if (shortestPath.getPathpoints().size() > 2) {
			poupateDirectionAndNavMsg(sourceMapEntity.getScale(), shortestPath,sourceMapEntity);
		} else {
			shortestPath.setDirection(PathwayUtils.Direction.STRAIGHT);
			shortestPath.setNavigationMsg("After reaching exit, go to level:" + destMapEntity.getMapName());
		}
		shortestPath.setUom(env.getProperty("redpine.am.indoornavigation-uom"));
		int estimatedTime = (int) Math
				.round(1 / (Double.parseDouble(env.getProperty("redpine.am.indoornavigation-estimated-uom-in-min")))
						* shortestPath.getDistance());
        if(shortestPath.getEstimatedTimeInMins()==null)
        {
            shortestPath.setEstimatedTimeInMins(estimatedTime);
        }
        if (estimatedTime == 0 && shortestPath.getEstimatedTimeInMins()!=0) {
            shortestPath.setEstimatedTimeInMins(1);
		} else {
			shortestPath.setEstimatedTimeInMins(estimatedTime);
		}
		return shortestPath;

	}

	/*
	 * This method is used to get logged in user current location and map
	 * details
	 */
	public Map<String, Object> getCurrentUserLocation(UserEntity userEntity) {
		Map<String, Object> map = new LinkedHashMap<>();
		UserTagRelationEntity userTagRelationEntity = userTagRelationRepository.findByUserByUserId(userEntity);
		if (userTagRelationEntity != null && userTagRelationEntity.getTagId() > 0) {
			TagEntity tagEntity = tagRepository.findById(userTagRelationEntity.getTagId());
			MapEntity mapEntity = null;
			if (tagEntity.getLastMapId() != null && tagEntity.getLastMapId() > 0) {
				mapEntity = mapRepository.findById(tagEntity.getLastMapId());
			} else if (tagEntity.getZoneId() != null && tagEntity.getZoneId() > 0) {
				ZoneEntity zoneEntity = zoneRepository.findById(tagEntity.getZoneId());
				mapEntity = mapRepository.findById(zoneEntity.getMapId());
			}
			if (mapEntity == null) {
				throw new NotFoundException(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
			}
			List<MapEntity> mapEntityList = mapRepository.findAll();
			map.put("username", userEntity.getLogonId());
			map.put("number_of_floor", mapEntityList.size());
			map.put("current_floor_name", mapEntity.getMapName());
            Map<String, Object> curFloorDetailsMap = new LinkedHashMap<>();
            curFloorDetailsMap.put("name", mapEntity.getMapName());
			curFloorDetailsMap.put("id", mapEntity.getId());
			curFloorDetailsMap.put("width", mapEntity.getWidth());
			curFloorDetailsMap.put("height", mapEntity.getHeight());
			curFloorDetailsMap.put("scale", mapEntity.getScale());
			curFloorDetailsMap.put("scaleX", mapEntity.getScaleX());
			curFloorDetailsMap.put("scaleY", mapEntity.getScaleY());
			map.put("current_floor_map_details", curFloorDetailsMap);
			Map<String, Object> curPositionDetailsMap = new LinkedHashMap<>();
			curPositionDetailsMap.put("x_value", tagEntity.getLastX()*mapEntity.getScaleX());
			curPositionDetailsMap.put("y_value", tagEntity.getLastY()*mapEntity.getScaleY());
			curPositionDetailsMap.put("anchorX", mapEntity.getOffsetX());
			curPositionDetailsMap.put("anchorY", mapEntity.getOffsetY());
			map.put("current_postion_details", curPositionDetailsMap);
		} else {
			throw new NotFoundException(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
		}

		return map;
	}

	/*
	 * This method is used to get user initial load details for indoor
	 * navigation
	 */
	public Map<String, Object> getCurrentUserInitialLoad(UserEntity userEntity) {
		Map<String, Object> map = new LinkedHashMap<>();
		UserTagRelationEntity userTagRelationEntity = userTagRelationRepository.findByUserByUserId(userEntity);
		if (userTagRelationEntity != null && userTagRelationEntity.getTagId() > 0) {
			TagEntity tagEntity = tagRepository.findById(userTagRelationEntity.getTagId());
			MapEntity mapEntity = null;
			if (tagEntity.getLastMapId() != null && tagEntity.getLastMapId() > 0) {
				mapEntity = mapRepository.findById(tagEntity.getLastMapId());
			} else if (tagEntity.getZoneId() != null && tagEntity.getZoneId() > 0) {
				ZoneEntity zoneEntity = zoneRepository.findById(tagEntity.getZoneId());
				mapEntity = mapRepository.findById(zoneEntity.getMapId());
			}
			if (mapEntity == null) {
				throw new NotFoundException(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
			}
			List<MapEntity> mapEntityList = mapRepository.findAll();
			map.put("username", userEntity.getLogonId());
			map.put("refreshInterval", Integer
					.parseInt(env.getProperty("redpine.am.indoornavigation-direction-refresh-interval-in-secs")));
			map.put("number_of_floor", mapEntityList.size());
			map.put("current_floor_name", mapEntity.getMapName());
			map.put("current_floor_id", mapEntity.getId());

			List<Map<String, Object>> floorMapDetailsList = new ArrayList<>();
			for (MapEntity mEntity : mapEntityList) {
				Map<String, Object> floorDetailsMap = new LinkedHashMap<>();
				floorDetailsMap.put("name", mEntity.getMapName());
				floorDetailsMap.put("id", mEntity.getId());
				floorDetailsMap.put("width", mEntity.getWidth());
				floorDetailsMap.put("height", mEntity.getHeight());
				floorDetailsMap.put("scale", mEntity.getScale());
				floorDetailsMap.put("scaleX", mEntity.getScaleX());
				floorDetailsMap.put("scaleY", mEntity.getScaleY());
				floorMapDetailsList.add(floorDetailsMap);
			}
			map.put("floor_map_details", floorMapDetailsList);
		} else {
			throw new NotFoundException(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
		}
		return map;
	}

	/*
	 * This method is used to get nodes, edges and obstacles for given map
	 */
	public JSONObject getNavigationData(Long mapId) {
		JSONObject jsonResponse = new JSONObject();
		MapEntity mapEntity = mapRepository.findById(mapId);
		List<JSONObject> pathJsonList = new ArrayList<>();
		List<PathwayPointEntity> pathList = pathwayPointRepository.findByMapByMapId(mapEntity);
		Map<Long, String> pathwayPointMap = new HashMap<>();
		for (PathwayPointEntity pathwayPointEntity : pathList) {
			JSONObject node = new JSONObject();
			node.put("name", pathwayPointEntity.getDisplayName());
			node.put("xCord", pathwayPointEntity.getxCord());
			node.put("yCord", pathwayPointEntity.getyCord());
			node.put("id", pathwayPointEntity.getId());
			pathJsonList.add(node);
			pathwayPointMap.put(pathwayPointEntity.getId(), pathwayPointEntity.getDisplayName());
		}
		List<JSONObject> lineJsonList = new ArrayList<>();
		List<PathwayLineEntity> lineList = pathwayLineRepository.findByMapByMapId(mapEntity);
		for (PathwayLineEntity pathwayLineEntity : lineList) {
			JSONObject edge = new JSONObject();
			edge.put("start", pathwayPointMap.get(pathwayLineEntity.getStartPointEntity().getId()));
			edge.put("end", pathwayPointMap.get(pathwayLineEntity.getEndPointEntity().getId()));
			edge.put("distance", pathwayLineEntity.getDistance());
			// edge.put("zoneId", pathwayLineEntity.getZoneId());
			edge.put("id", pathwayLineEntity.getId());
			lineJsonList.add(edge);
		}
		List<JSONObject> obstacleJsonList = new ArrayList<>();
		List<PathwayObstacleEntity> obstacleList = pathwayObstacleRepository.findByMapByMapId(mapEntity);
		for (PathwayObstacleEntity pathwayObstacleEntity : obstacleList) {
			JSONObject obstacle = new JSONObject();
			obstacle.put("start",
					pathwayPointMap.get(pathwayObstacleEntity.getPathwayLineEntity().getStartPointEntity().getId()));
			obstacle.put("end",
					pathwayPointMap.get(pathwayObstacleEntity.getPathwayLineEntity().getEndPointEntity().getId()));
			obstacle.put("xCord", pathwayObstacleEntity.getxCord());
			obstacle.put("yCord", pathwayObstacleEntity.getyCord());
			obstacle.put("id", pathwayObstacleEntity.getId());
			obstacleJsonList.add(obstacle);
		}
		if (mapEntity != null) {
			jsonResponse.put("mapId", mapEntity.getId());
			jsonResponse.put("mapName", mapEntity.getMapName());
		}
		jsonResponse.put("nodes", pathJsonList);
		jsonResponse.put("edges", lineJsonList);
		jsonResponse.put("obstructions", obstacleJsonList);
		return jsonResponse;
	}

	/*
	 * This method is used to save nodes, edges and obstacles for given map
	 */
	@Transactional
	public String saveNavigationData(JSONObject json, Long mapId, MapEntity mapEntity) {

		pathwayObstacleRepository.deleteByMapByMapId(mapEntity);
		pathwayLineRepository.deleteByMapByMapId(mapEntity);
		pathwayPointRepository.deleteByMapByMapId(mapEntity);
		JSONArray nodes = (JSONArray) json.get("nodes");
		Map<String, PathwayPointEntity> pathwayPointMap = new HashMap<>();
		for (int i = 0; i < nodes.size(); i++) {
			JSONObject node = (JSONObject) nodes.get(i);
			PathwayPointEntity pathwayPointEntity = new PathwayPointEntity();
			pathwayPointEntity.setDisplayName((String) node.get("name"));
			pathwayPointEntity.setxCord(nodeValueToDouble(node.get("xCord")));
			pathwayPointEntity.setyCord(nodeValueToDouble(node.get("yCord")));
			pathwayPointEntity.setMapByMapId(mapEntity);
			pathwayPointEntity = pathwayPointRepository.saveAndFlush(pathwayPointEntity);
			pathwayPointMap.put((String) node.get("name"), pathwayPointEntity);
		}
		MultiKeyMap pathLineMultiKeyMap = MultiKeyMap.decorate(new LinkedMap());
		JSONArray edges = (JSONArray) json.get("edges");
		for (int i = 0; i < edges.size(); i++) {
			try {
				JSONObject edge = (JSONObject) edges.get(i);
				PathwayLineEntity pathwayLineEntity = new PathwayLineEntity();
				pathwayLineEntity.setStartPointEntity(pathwayPointMap.get(edge.get("start")));
				pathwayLineEntity.setEndPointEntity(pathwayPointMap.get(edge.get("end")));
				pathwayLineEntity.setMapByMapId(mapEntity);
				Point2D start = new Point2D.Double(pathwayPointMap.get(edge.get("start")).getxCord()/mapEntity.getScaleX(),
						pathwayPointMap.get(edge.get("start")).getyCord()/mapEntity.getScaleY());
				Point2D end = new Point2D.Double(pathwayPointMap.get(edge.get("end")).getxCord()/mapEntity.getScaleX(),
						pathwayPointMap.get(edge.get("end")).getyCord()/mapEntity.getScaleY());
				pathwayLineEntity.setDistance(getDistanceBetweenTwoPoints(start, end));
				pathwayLineEntity.setZoneId(null);
				pathwayLineEntity = pathwayLineRepository.saveAndFlush(pathwayLineEntity);
				pathLineMultiKeyMap.put(edge.get("start"), edge.get("end"), pathwayLineEntity);
			} catch (Exception ex) {
				throw new NotFoundException(PathwayUtils.ErrorMeesages.NODE_DELETION_FAILED);
			}
		}
		JSONArray obstructions = (JSONArray) json.get("obstructions");
		for (int i = 0; i < obstructions.size(); i++) {
			try {
				JSONObject obstruction = (JSONObject) obstructions.get(i);
				PathwayObstacleEntity pathwayObstacleEntity = new PathwayObstacleEntity();
				pathwayObstacleEntity.setPathwayLineEntity(
						(PathwayLineEntity) pathLineMultiKeyMap.get(obstruction.get("start"), obstruction.get("end")));
				pathwayObstacleEntity.setxCord(nodeValueToDouble(obstruction.get("xCord")));
				pathwayObstacleEntity.setyCord(nodeValueToDouble(obstruction.get("yCord")));
				pathwayObstacleEntity.setMapByMapId(mapEntity);
				pathwayObstacleRepository.saveAndFlush(pathwayObstacleEntity);
			} catch (Exception ex) {
				throw new NotFoundException(PathwayUtils.ErrorMeesages.PATH_DELETION_FAILED);
			}
		}
		json = getNavigationData(mapId);
		return json.toString();
	}

	/*
	 * This method is used to update nodes, edges and obstacles for given map
	 */
	@Transactional
	public String updateNavigationData(JSONObject json, Long mapId, MapEntity mapEntity, Double scale) {
		Set<Long> existingDBObstacleIds = new HashSet<>();
		List<PathwayObstacleEntity> existingObstacleList = pathwayObstacleRepository.findByMapByMapId(mapEntity);
		for (PathwayObstacleEntity existObj : existingObstacleList) {
			existingDBObstacleIds.add(existObj.getId());
		}

		Set<Long> existingDBPathlineIds = new HashSet<>();
		List<PathwayLineEntity> existingLineList = pathwayLineRepository.findByMapByMapId(mapEntity);
		for (PathwayLineEntity existObj : existingLineList) {
			existingDBPathlineIds.add(existObj.getId());
		}

		Set<Long> existingDBPathPointIds = new HashSet<>();
		List<PathwayPointEntity> existingPathList = pathwayPointRepository.findByMapByMapId(mapEntity);
		for (PathwayPointEntity existObj : existingPathList) {
			existingDBPathPointIds.add(existObj.getId());
		}

		JSONArray nodes = (JSONArray) json.get("nodes");
		Map<String, PathwayPointEntity> pathwayPointMap = new HashMap<>();
		Set<Long> existingPathPointIds = new HashSet<>();
		for (int i = 0; i < nodes.size(); i++) {
			JSONObject node = (JSONObject) nodes.get(i);
			PathwayPointEntity pathwayPointEntity = new PathwayPointEntity();
			if (node.containsKey("id") && node.get("id") != null && (Long) node.get("id") > 0) {
				pathwayPointEntity.setId((Long) node.get("id"));
				existingPathPointIds.add((Long) node.get("id"));
			}
			pathwayPointEntity.setDisplayName((String) node.get("name"));
			pathwayPointEntity.setxCord(nodeValueToDouble(node.get("xCord")));
			pathwayPointEntity.setyCord(nodeValueToDouble(node.get("yCord")));
			pathwayPointEntity.setMapByMapId(mapEntity);
			pathwayPointEntity = pathwayPointRepository.saveAndFlush(pathwayPointEntity);
			node.put("id", pathwayPointEntity.getId());
			pathwayPointMap.put((String) node.get("name"), pathwayPointEntity);
		}

		Set<Long> existingPathLineIds = new HashSet<>();
		MultiKeyMap pathLineMultiKeyMap = MultiKeyMap.decorate(new LinkedMap());
		JSONArray edges = (JSONArray) json.get("edges");
		for (int i = 0; i < edges.size(); i++) {
			JSONObject edge = (JSONObject) edges.get(i);
			PathwayLineEntity pathwayLineEntity = new PathwayLineEntity();
			if (edge.containsKey("id") && edge.get("id") != null && (Long) edge.get("id") > 0) {
				pathwayLineEntity.setId((Long) edge.get("id"));
				existingPathLineIds.add((Long) edge.get("id"));
			}
			pathwayLineEntity.setStartPointEntity(pathwayPointMap.get(edge.get("start")));
			pathwayLineEntity.setEndPointEntity(pathwayPointMap.get(edge.get("end")));
			pathwayLineEntity.setMapByMapId(mapEntity);
			Point2D start = new Point2D.Double(pathwayPointMap.get(edge.get("start")).getxCord(),
					pathwayPointMap.get(edge.get("start")).getyCord());
			Point2D end = new Point2D.Double(pathwayPointMap.get(edge.get("end")).getxCord(),
					pathwayPointMap.get(edge.get("end")).getyCord());
			pathwayLineEntity.setDistance(getDistanceBetweenTwoPoints(start, end));
			pathwayLineEntity = pathwayLineRepository.saveAndFlush(pathwayLineEntity);
			edge.put("id", pathwayLineEntity.getId());
			pathLineMultiKeyMap.put(edge.get("start"), edge.get("end"), pathwayLineEntity);
		}
		Set<Long> existingObstacleIds = new HashSet<>();
		JSONArray obstructions = (JSONArray) json.get("obstructions");
		for (int i = 0; i < obstructions.size(); i++) {
			JSONObject obstruction = (JSONObject) obstructions.get(i);
			PathwayObstacleEntity pathwayObstacleEntity = new PathwayObstacleEntity();
			if (obstruction.containsKey("id") && obstruction.get("id") != null && (Long) obstruction.get("id") > 0) {
				pathwayObstacleEntity.setId((Long) obstruction.get("id"));
				existingObstacleIds.add((Long) obstruction.get("id"));
			}
			pathwayObstacleEntity.setPathwayLineEntity(
					(PathwayLineEntity) pathLineMultiKeyMap.get(obstruction.get("start"), obstruction.get("end")));
			pathwayObstacleEntity.setxCord(nodeValueToDouble(obstruction.get("xCord")));
			pathwayObstacleEntity.setyCord(nodeValueToDouble(obstruction.get("yCord")));
			pathwayObstacleEntity.setMapByMapId(mapEntity);
			pathwayObstacleEntity = pathwayObstacleRepository.saveAndFlush(pathwayObstacleEntity);
			obstruction.put("id", pathwayObstacleEntity.getId());
			existingObstacleIds.add(pathwayObstacleEntity.getId());
		}

		Set<Long> obstacleDiffSet = Sets.symmetricDifference(existingDBObstacleIds, existingObstacleIds);
		for (Long id : obstacleDiffSet) {
			pathwayObstacleRepository.delete(id);
		}

		Set<Long> lineDiffSet = Sets.symmetricDifference(existingDBPathlineIds, existingPathLineIds);
		for (Long id : lineDiffSet) {
			pathwayLineRepository.delete(id);
		}

		Set<Long> pointDiffSet = Sets.symmetricDifference(existingDBPathPointIds, existingPathPointIds);
		for (Long id : pointDiffSet) {
			pathwayPointRepository.delete(id);
		}
		if (mapEntity != null) {
			json.put("mapId", mapEntity.getId());
			json.put("mapName", mapEntity.getMapName());
		}
		return json.toString();
	}

	/*
	 * This method is used to get shortest path between source node and
	 * destination node in a given map using Dijkstra shortest path algorithm
	 */
	@SuppressWarnings("unchecked")
	private Object getShortestPath(Long mapId, String source, String destination) {
		try {
			MapEntity mapEntity = mapRepository.findById(mapId);
			List<PathwayLineEntity> pathLineEntities = getPathwayLines(mapId);
			List<PathwayPointEntity> pathwayPoints = getNodes(mapId);
			ListIterator<PathwayLineEntity> iterate = pathLineEntities.listIterator();
			while (iterate.hasNext()) {
				PathwayLineEntity pathLineEntity = iterate.next();
				PathwayLineEntity pathway = new PathwayLineEntity();
				pathway.setDistance(pathLineEntity.getDistance());
				pathway.setEndPointEntity(pathLineEntity.getStartPointEntity());
				pathway.setStartPointEntity(pathLineEntity.getEndPointEntity());
				pathway.setEndPointId(pathLineEntity.getStartPointId());
				pathway.setStartPointId(pathLineEntity.getEndPointId());
				pathway.setMapId(pathLineEntity.getMapId());
				pathway.setMapByMapId(pathLineEntity.getMapByMapId());
				iterate.add(pathway);
			}
			ShortestPath graph = new ShortestPath(pathwayPoints, pathLineEntities);
			ShortestPathService shortestPath = new ShortestPathService(graph);
			JSONObject response = new JSONObject();
			JSONArray paths = new JSONArray();
			if (pathwayPoints.isEmpty()) {
				return "No path exists";
			}
			shortestPath.execute(
					pathwayPoints.stream().filter(x -> x.getDisplayName().equalsIgnoreCase(source)).findFirst().get());
			LinkedList<PathwayPointEntity> path = shortestPath.getPath(pathwayPoints.stream()
					.filter(x -> x.getDisplayName().equalsIgnoreCase(destination)).findFirst().get());
			if (path == null) {
				return "No path exists";
			} else {
				for (PathwayPointEntity vertex : path) {
					JSONObject point = new JSONObject();
					point.put("name", vertex.getDisplayName());
					point.put("xCord", vertex.getxCord());
					point.put("yCord", vertex.getyCord());
					paths.add(point);
				}
				response.put(
						"distance",
												shortestPath.distance
														.get(pathwayPoints.stream()
																.filter(x -> x.getDisplayName()
																		.equalsIgnoreCase(destination))
																.findFirst().get()));

				response.put("shortestPath", paths);
			System.out.println("response::::"+response.toJSONString());
				return response;
			}
		} catch (NoSuchElementException ex) {
			logger.error("Exception while getting shortest path using Dijkstra shortest path algorithm", ex);
			return "No path exists";
		}
	}
	/*
	 * This method is used to populate shortest path response distance and path
	 * points
	 */
	private void populateShortestPathResponse(MapEntity mapEntity, ShortestPathResponse shortestPathResponse, JSONObject shortestPath,
			PathwayLineEntity nearestSourceLine, PathwayLineEntity nearestDestLine, Point2D sourceClosestPoint,
			Point2D destClosestPoint) {
		shortestPathResponse.setDistance((Double) shortestPath.get("distance"));
		JSONArray paths = (JSONArray) shortestPath.get("shortestPath");

		if (!nearestDestLine.getId().equals(nearestSourceLine.getId())) {
			int i = 0;
			for (Object path : paths) {
				System.out.println("json path::::"+(JSONObject)path);
				System.out.println("source closest point:::::"+sourceClosestPoint);
				System.out.println("dest closest point:::::"+destClosestPoint);
				if (i == 0) {
					double x = (double) ((JSONObject) path).get("xCord");
					double y = (double) ((JSONObject) path).get("yCord");
					if (x != sourceClosestPoint.getX()*mapEntity.getScaleX() || 
							y != sourceClosestPoint.getY()*mapEntity.getScaleY()) {
						JSONObject sourceClosestObject = new JSONObject();
						sourceClosestObject.put("name", "");
						sourceClosestObject.put("xCord", sourceClosestPoint.getX());
						sourceClosestObject.put("yCord", sourceClosestPoint.getY());
						shortestPathResponse.getPathpoints().add(sourceClosestObject);
					}
				
				}
				 if (i == (paths.size() - 1)) {
					shortestPathResponse.getPathpoints().add((JSONObject) path);
					double x = (double) ((JSONObject) path).get("xCord");
					double y = (double) ((JSONObject) path).get("yCord");
					if (x != destClosestPoint.getX()*mapEntity.getScaleX() || y != destClosestPoint.getY()*mapEntity.getScaleY()) {
						JSONObject destClosestObject = new JSONObject();
						destClosestObject.put("name", "");
						destClosestObject.put("xCord", destClosestPoint.getX());
						destClosestObject.put("yCord", destClosestPoint.getY());
						shortestPathResponse.getPathpoints().add(destClosestObject);
					}
				}
				else{
					
					JSONObject pathInMetres = (JSONObject)path;
					pathInMetres.put("name", ((JSONObject) path).get("name"));
					pathInMetres.put("xCord",(double) ((JSONObject) path).get("xCord"));
					pathInMetres.put("yCord",(double) ((JSONObject) path).get("yCord"));
					shortestPathResponse.getPathpoints().add(pathInMetres);
				}
				i++;
			}
		} else {
			System.out.println("inside else::::");
			JSONObject sourceClosestObject = new JSONObject();
            MapEntity me=new MapEntity();
            me.setId(nearestSourceLine.getMapId());
            double distance =getDistanceBetweenTwoPoints(sourceClosestPoint,destClosestPoint);
			if (distance <= Double.valueOf(env.getProperty("redpine.am.indoornavigation.min.distance"))) {
				shortestPathResponse.setDistance(0.0);
				shortestPathResponse.setEstimatedTimeInMins(0);
			} else {
				shortestPathResponse.setDistance(distance);
			}
            sourceClosestObject.put("name", "");
			sourceClosestObject.put("xCord", sourceClosestPoint.getX());
			sourceClosestObject.put("yCord", sourceClosestPoint.getY());
			shortestPathResponse.getPathpoints().add(sourceClosestObject);
			JSONObject destClosestObject = new JSONObject();
			destClosestObject.put("name", "");
			destClosestObject.put("xCord", destClosestPoint.getX());
			destClosestObject.put("yCord", destClosestPoint.getY());
			shortestPathResponse.getPathpoints().add(destClosestObject);
		}
	}

	/*
	 * This method is used to get final shortest path in between between source
	 * and destination points
	 */
	private JSONObject getFinalShortestPath(PathwayLineEntity nearestSourceLine, PathwayLineEntity nearestDestLine,
			MapEntity sourceMapEntity, MapEntity destMapEntity, TagEntity sourceTagEntity, TagEntity destTagEntity,
			Point2D sourceClosestPoint, Point2D destClosestPoint) {
		//MapScaleEntity sourceMapScale = mapScaleRepository.findByMapByMapId(sourceMapEntity);
		//MapScaleEntity destMapScale = mapScaleRepository.findByMapByMapId(destMapEntity);
		Object firstShortestPath = getShortestPath(sourceMapEntity.getId(),
				nearestSourceLine.getStartPointEntity().getDisplayName(),
				nearestDestLine.getStartPointEntity().getDisplayName());
		Object secondShortestPath = getShortestPath(sourceMapEntity.getId(),
				nearestSourceLine.getStartPointEntity().getDisplayName(),
				nearestDestLine.getEndPointEntity().getDisplayName());
		Object thirdShortestPath = getShortestPath(sourceMapEntity.getId(),
				nearestSourceLine.getEndPointEntity().getDisplayName(),
				nearestDestLine.getStartPointEntity().getDisplayName());
		Object fourthShortestPath = getShortestPath(sourceMapEntity.getId(),
				nearestSourceLine.getEndPointEntity().getDisplayName(),
				nearestDestLine.getEndPointEntity().getDisplayName());
		Double firstPathDistance = Double.MAX_VALUE;
		if (firstShortestPath instanceof JSONObject) {
			firstPathDistance = (Double) ((JSONObject) firstShortestPath).get("distance");
			double sourceDistanceDiff = getDistanceBetweenTwoPoints(sourceClosestPoint,
					new Point2D.Double(nearestSourceLine.getStartPointEntity().getxCord(),
							nearestSourceLine.getStartPointEntity().getyCord()));
			double destDistanceDiff = getDistanceBetweenTwoPoints(destClosestPoint,
					new Point2D.Double(nearestDestLine.getStartPointEntity().getxCord(),
							nearestDestLine.getStartPointEntity().getyCord()));
			firstPathDistance = firstPathDistance
					+ (sourceDistanceDiff  + destDistanceDiff);
            ((JSONObject) firstShortestPath).put("distance", firstPathDistance);
        }
		Double secondPathDistance = Double.MAX_VALUE;
		if (secondShortestPath instanceof JSONObject) {
			secondPathDistance = (Double) ((JSONObject) secondShortestPath).get("distance");
			double sourceDistanceDiff = getDistanceBetweenTwoPoints(sourceClosestPoint,
					new Point2D.Double(nearestSourceLine.getStartPointEntity().getxCord(),
							nearestSourceLine.getStartPointEntity().getyCord()));
			double destDistanceDiff = getDistanceBetweenTwoPoints(destClosestPoint, new Point2D.Double(
					nearestDestLine.getEndPointEntity().getxCord(), nearestDestLine.getEndPointEntity().getyCord()));
			secondPathDistance = secondPathDistance
					+ (sourceDistanceDiff + destDistanceDiff);
            ((JSONObject) secondShortestPath).put("distance", secondPathDistance);
        }
		Double thirdPathDistance = Double.MAX_VALUE;
		if (thirdShortestPath instanceof JSONObject) {
			thirdPathDistance = (Double) ((JSONObject) thirdShortestPath).get("distance");
			double sourceDistanceDiff = getDistanceBetweenTwoPoints(sourceClosestPoint,
					new Point2D.Double(nearestSourceLine.getEndPointEntity().getxCord(),
							nearestSourceLine.getEndPointEntity().getyCord()));
			double destDistanceDiff = getDistanceBetweenTwoPoints(destClosestPoint,
					new Point2D.Double(nearestDestLine.getStartPointEntity().getxCord().intValue(),
							nearestDestLine.getStartPointEntity().getyCord().intValue()));
			thirdPathDistance = thirdPathDistance
					+ (sourceDistanceDiff  + destDistanceDiff );
            ((JSONObject) thirdShortestPath).put("distance", thirdPathDistance);
        }
		Double fourthPathDistance = Double.MAX_VALUE;
		if (fourthShortestPath instanceof JSONObject) {
			fourthPathDistance = (Double) ((JSONObject) fourthShortestPath).get("distance");
			double sourceDistanceDiff = getDistanceBetweenTwoPoints(sourceClosestPoint,
					new Point2D.Double(nearestSourceLine.getEndPointEntity().getxCord(),
							nearestSourceLine.getEndPointEntity().getyCord()));
			double destDistanceDiff = getDistanceBetweenTwoPoints(destClosestPoint,
					new Point2D.Double(nearestDestLine.getEndPointEntity().getxCord().intValue(),
							nearestDestLine.getEndPointEntity().getyCord().intValue()));
			fourthPathDistance = fourthPathDistance
					+ (sourceDistanceDiff  + destDistanceDiff );
            ((JSONObject) fourthShortestPath).put("distance", fourthPathDistance);
        }
		if (firstPathDistance < secondPathDistance && firstPathDistance < thirdPathDistance
				&& firstPathDistance < fourthPathDistance) {
			return ((JSONObject) firstShortestPath);
		} else if (secondPathDistance < firstPathDistance && secondPathDistance < thirdPathDistance
				&& secondPathDistance < fourthPathDistance) {
			return ((JSONObject) secondShortestPath);
		} else if (thirdPathDistance < firstPathDistance && thirdPathDistance < secondPathDistance
				&& thirdPathDistance < fourthPathDistance) {
			return ((JSONObject) thirdShortestPath);
		} else if (fourthPathDistance < firstPathDistance && fourthPathDistance < secondPathDistance
				&& fourthPathDistance < thirdPathDistance) {
			return ((JSONObject) fourthShortestPath);
		}
		return null;
	}

	/*
	 * This method is used to populate direction and navigation message based on
	 * shortest path
	 */
	private void poupateDirectionAndNavMsg(double scale, ShortestPathResponse shortestPathResponse,MapEntity mapEntity) {
		List<JSONObject> pathPoints = shortestPathResponse.getPathpoints();
		
		JSONObject start = pathPoints.get(0);
		JSONObject nearest = pathPoints.get(2);
		String uom = env.getProperty("redpine.am.indoornavigation-uom");
		Point2D p1 = new Point2D.Double(Double.parseDouble(start.get("xCord").toString())/mapEntity.getScaleX(),
				Double.parseDouble(start.get("yCord").toString())/mapEntity.getScaleY());
		Point2D p2 = new Point2D.Double(Double.parseDouble(pathPoints.get(1).get("xCord").toString())/mapEntity.getScaleX(),
				Double.parseDouble(pathPoints.get(1).get("yCord").toString())/mapEntity.getScaleY());
		Point2D p3 = new Point2D.Double(Double.parseDouble(pathPoints.get(2).get("xCord").toString())/mapEntity.getScaleX(),
				Double.parseDouble(pathPoints.get(2).get("yCord").toString())/mapEntity.getScaleY());
		String message = null;
		if (getAngle(p1, p2) >= 0 && getAngle(p1, p2) <= 88) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.RIGHT);
		} else if (getAngle(p1, p2) > 88 && getAngle(p1, p2) <= 92) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.STRAIGHT);
		} else if (getAngle(p1, p2) > 92 && getAngle(p1, p2) <= 182) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.LEFT);
		} else if (getAngle(p1, p2) > 182 && getAngle(p1, p2) < 272) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.LEFT);
		} else if (getAngle(p1, p2) >= 272 && getAngle(p1, p2) < 274) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.STRAIGHT);
		} else if (getAngle(p1, p2) > 274) {
			shortestPathResponse.setDirection(PathwayUtils.Direction.RIGHT);
		}

		int temp = Integer.parseInt(env.getProperty("redpine.am.indoornavigation-direction-buffer-max-size"));

		if ((getDifference(Double.parseDouble(nearest.get("xCord").toString()),
				Double.parseDouble(start.get("xCord").toString()))) > temp
				&& (getDifference(Double.parseDouble(nearest.get("yCord").toString()),
						Double.parseDouble(start.get("yCord").toString())) > temp)) {
			if (getDirection(p1, p3, p2) > 0) {
				message = "Turn left after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.LEFT);
			} else if (getDirection(p1, p3, p2) < 0) {
				message = "Turn right after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.RIGHT);
			} else {
				message = "Go straight ";
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.STRAIGHT);
			}
		}

		if ((getDifference(Double.parseDouble(nearest.get("xCord").toString()),
				Double.parseDouble(start.get("xCord").toString()))) < temp
				&& (getDifference(Double.parseDouble(nearest.get("yCord").toString()),
						Double.parseDouble(start.get("yCord").toString())) < temp)) {
			if (getDirection(p1, p3, p2) > 0) {

				message = "Turn left after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.LEFT);
			} else if (getDirection(p1, p3, p2) < 0) {
				message = "Turn right after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.RIGHT);
			} else {
				message = "Go straight ";
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.STRAIGHT);
			}
		}

		if ((getDifference(Double.parseDouble(nearest.get("xCord").toString()),
				Double.parseDouble(start.get("xCord").toString()))) > temp
				&& (getDifference(Double.parseDouble(nearest.get("yCord").toString()),
						Double.parseDouble(start.get("yCord").toString())) < temp)) {
			if (getDirection(p1, p3, p2) > 0) {
				message = "Turn left after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.LEFT);
			} else if (getDirection(p1, p3, p2) < 0) {
				message = "Turn right after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.RIGHT);
			} else {
				message = "Go straight ";
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.STRAIGHT);
			}
		}

		if ((getDifference(Double.parseDouble(nearest.get("xCord").toString()),
				Double.parseDouble(start.get("xCord").toString()))) < temp
				&& (getDifference(Double.parseDouble(nearest.get("yCord").toString()),
						Double.parseDouble(start.get("yCord").toString())) > temp)) {
			if (getDirection(p1, p3, p2) > 0) {
				message = "Turn left after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.LEFT);
			} else if (getDirection(p1, p3, p2) < 0) {
				message = "Turn right after " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " "
						+ uom;
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.RIGHT);
			} else {
				message = "Go straight ";
				shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.STRAIGHT);
			}
		}

		if ((Double.parseDouble(nearest.get("xCord").toString()) != Double.parseDouble(start.get("xCord").toString())
				&& (getDifference(Double.parseDouble(nearest.get("yCord").toString()),
						Double.parseDouble(start.get("yCord").toString())) < temp))
				|| (Double.parseDouble(nearest.get("yCord").toString()) != Double
						.parseDouble(start.get("yCord").toString())
						&& (getDifference(Double.parseDouble(nearest.get("xCord").toString()),
								Double.parseDouble(start.get("xCord").toString())) < temp))) {

			message = "Go straight upto " + String.format("%.2f", getDistanceBetweenTwoPoints(p1, p2)) + " " + uom;
			shortestPathResponse.setNavigationDirection(PathwayUtils.Direction.STRAIGHT);
		}
		shortestPathResponse.setNavigationMsg(message);
	}

	/*
	 * This method is used to get exit points for given map
	 */
	public JSONObject getPathwayExitpointData(Long mapId) {
		JSONObject jsonResponse = new JSONObject();
		MapEntity mapEntity = mapRepository.findById(mapId);
		List<JSONObject> pathJsonList = new ArrayList<>();
		List<MapEntity> listMap = mapRepository.findAll();
		Map<Long, String> map = new HashMap<>();
		for (MapEntity mapData : listMap) {
			map.put(mapData.getId(), mapData.getMapName());
		}
		List<PathwayExitPointEntity> pathList = pathwayExitPointRepository.findByMapByMapId(mapEntity);
		for (PathwayExitPointEntity pathwayExitPointEntity : pathList) {
			List<String> mapNames = new ArrayList<String>();
			JSONObject node = new JSONObject();
			node.put("name", pathwayExitPointEntity.getName());
            node.put("xCord", pathwayExitPointEntity.getxCord());
            node.put("yCord", pathwayExitPointEntity.getyCord());
            node.put("id", pathwayExitPointEntity.getId());
			node.put("multiFloor", Arrays.asList((pathwayExitPointEntity.getRoutes()).split(",")).stream()
					.map(String::trim).mapToLong(Long::parseLong).toArray());
			long[] numbers = Arrays.asList(pathwayExitPointEntity.getRoutes().split(",")).stream().map(String::trim)
					.mapToLong(Long::parseLong).toArray();
			for (long number : numbers) {
				mapNames.add(map.get(number));
			}
			node.put("mapNames", mapNames);
			pathJsonList.add(node);
		}
		jsonResponse.put("exitPoints", pathJsonList);
		return jsonResponse;
	}

	/*
	 * This method is used to save exit points for given map
	 */
	@Transactional
	public List<PathwayExitPointEntity> savePathwayExitPointList(List<PathwayExitPointEntity> list, Long mapId) {
		List<PathwayExitPointEntity> returnList = new ArrayList<PathwayExitPointEntity>();
		MapEntity mapEntity = mapRepository.findById(mapId);
		pathwayExitPointRepository.deleteByMapByMapId(mapEntity);
		for (PathwayExitPointEntity obj : list) {
			obj.setId(null);
            obj.setxCord(obj.getxCord());
            obj.setyCord(obj.getyCord());
            obj.setRoutes(StringUtils.join(obj.getMultiFloor(), ','));
			obj.setMapByMapId(mapEntity);
			returnList.add(pathwayExitPointRepository.saveAndFlush(obj));
		}
		return returnList;
	}

	/*
	 * This method is used to update distance for pathway lines if scale is
	 * modified
	 */
	public void updateDistance(Long mapId, double mapScaleX,double mapScaleY) {
		
		MapEntity mapEntity = new MapEntity();
		mapEntity.setId(mapId);
		List<PathwayLineEntity> pathwayLines = pathwayLineRepository.findByMapByMapId(mapEntity);
		ListIterator<PathwayLineEntity> iterate = pathwayLines.listIterator();
		while (iterate.hasNext()) {
			PathwayLineEntity start = iterate.next();
			Point2D startPoint = new Point2D.Double(start.getStartPointEntity().getxCord()/mapScaleX,
					start.getStartPointEntity().getyCord()/mapScaleY);
			Point2D endPoint = new Point2D.Double(start.getEndPointEntity().getxCord()/mapScaleX,
					start.getEndPointEntity().getyCord()/mapScaleY);
			start.setDistance(getDistanceBetweenTwoPoints(startPoint, endPoint));
			pathwayLineRepository.saveAndFlush(start);
		}

	}

	/*
	 * This method is used to get nearest exit point shortest path if source and
	 * destination in different maps
	 */
	private ShortestPathResponse getNearestExitShortestPath(ShortestPathResponse shortestPathResponse,
			TagEntity sourceTagEntity, TagEntity destTagEntity, PathwayZoneEntity sourceZoneEntity,
			PathwayZoneEntity destZoneEntity, MapEntity sourceMapEntity, MapEntity destMapEntity) {
		List<PathwayExitPointEntity> exitpoints = getNearestExitPoint(sourceMapEntity, destMapEntity);
		List<ShortestPathResponse> availablePaths = new ArrayList<>();
		for (PathwayExitPointEntity exit : exitpoints) {
			destTagEntity.setLastX(exit.getxCord());
			destTagEntity.setLastY(exit.getyCord());
			destZoneEntity = getPathwayZoneByPoint(sourceMapEntity, destTagEntity);
            shortestPathResponse.setStartpoint(new Point2D.Double(sourceTagEntity.getLastX()*sourceMapEntity.getScaleX(),
                    sourceTagEntity.getLastY()*sourceMapEntity.getScaleY()));
            shortestPathResponse.setEndpoint(new Point2D.Double(destTagEntity.getLastX(),destTagEntity.getLastY()));
            if (destZoneEntity == null || sourceZoneEntity == null) {
				shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
				shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
				break;
			}
			logger.info(
					"dest zone : " + destZoneEntity.getZoneName() + " source zone : " + sourceZoneEntity.getZoneName());
			List<PathwayPointEntity> sourceMapPointList = pathwayPointRepository.findByMapByMapId(sourceMapEntity);
			List<PathwayPointEntity> destMapPointList = pathwayPointRepository.findByMapByMapId(sourceMapEntity);
			PathwayLineEntity nearestDestPathLine = getNearestPathLine(destTagEntity,
					getPathwayLinesInZone(sourceMapEntity, destMapPointList, destZoneEntity));
			PathwayLineEntity nearestSourcePathLine = getNearestPathLine(sourceTagEntity,
					getPathwayLinesInZone(sourceMapEntity, sourceMapPointList, sourceZoneEntity));

			if (nearestDestPathLine != null && nearestSourcePathLine != null) {
				logger.info("nearestDestPathLine : " + nearestDestPathLine);
				logger.info("nearestSourcePathLine : " + nearestSourcePathLine);
				Point2D sourceClosestPoint = PathwayUtils.getClosestPointOnSegment(
						nearestSourcePathLine.getStartPointEntity(), nearestSourcePathLine.getEndPointEntity(),
						new Point2D.Double(sourceTagEntity.getLastX()*sourceMapEntity.getScaleX(), sourceTagEntity.getLastY()*sourceMapEntity.getScaleY()));
				Point2D destClosestPoint = PathwayUtils.getClosestPointOnSegment(
						nearestDestPathLine.getStartPointEntity(), nearestDestPathLine.getEndPointEntity(),
						new Point2D.Double(destTagEntity.getLastX(), destTagEntity.getLastY()));
				JSONObject shortestPath = getFinalShortestPath(nearestSourcePathLine, nearestDestPathLine,
						sourceMapEntity, sourceMapEntity, sourceTagEntity, destTagEntity, sourceClosestPoint,
						destClosestPoint);
				if (shortestPath == null) {
					shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
					shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
					break;
				}
				logger.info("shortestPath : " + shortestPath);
				populateShortestPathResponse(sourceMapEntity, shortestPathResponse, shortestPath, nearestSourcePathLine,
						nearestDestPathLine, sourceClosestPoint, destClosestPoint);
				shortestPathResponse.setStatus(PathwayUtils.Status.SUCCESS);
				availablePaths.add(shortestPathResponse);
				
			} else {
				
				shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
				shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
			}
		}
		if (availablePaths.size() > 0) {
			double tempDistance = availablePaths.get(0).getDistance();
			for (ShortestPathResponse shortest : availablePaths) {
				if (!shortest.getPathpoints().isEmpty() || shortest.getPathpoints() != null) {
					if (shortest.getDistance() <= tempDistance) {
						tempDistance = shortest.getDistance();
						shortestPathResponse.setPathpoints(shortest.getPathpoints());
                        shortestPathResponse.setDistance(shortest.getDistance());
                    }
				}
			}
		} else {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.NO_PATH_FOUND_FOR_ASSET);
			shortestPathResponse.setPathpoints(null);
		}
		return shortestPathResponse;
	}

	/*
	 * This method is used to get nearest exit point for user location in a
	 * given map
	 */
	private List<PathwayExitPointEntity> getNearestExitPoint(MapEntity sourceMapEntity, MapEntity destMapEntity) {
		List<PathwayExitPointEntity> exitPoints = pathwayExitPointRepository.findByMapByMapId(sourceMapEntity);
		ListIterator<PathwayExitPointEntity> iterate = exitPoints.listIterator();
		while (iterate.hasNext()) {
			PathwayExitPointEntity exit = iterate.next();
			String[] routes = exit.getRoutes().split(",");
			boolean status = false;
			for (int k = 0; k < routes.length; k++) {
				if (Long.parseLong(routes[k]) == destMapEntity.getId()) {
					status = true;
				}
			}
			if (status == false) {
				iterate.remove();
			}
		}
		return exitPoints;
	}

	/*
	 * This method is used to get pathway zone for given tag coordinates
	 */
	private PathwayZoneEntity getPathwayZoneByPoint(MapEntity mapEntity, TagEntity tagEntity) {
		List<PathwayZoneEntity> pathwayZoneEntityList = pathwayZoneRepository.findByMapByMapId(mapEntity);
		for (PathwayZoneEntity pathwayZone : pathwayZoneEntityList) {
			if (StringUtils.isBlank(pathwayZone.getPolygon())) {
				continue;
			}
            Point2D point = new Point2D.Double(tagEntity.getLastX(),
                    tagEntity.getLastY());
            Polygon polygon = new Polygon();
			String[] pointArray = pathwayZone.getPolygon().split(";");
			for (String p : pointArray) {
				String[] pointCoOrdinates = p.split(",");
				if (pointCoOrdinates.length == 2 && !StringUtils.isBlank(pointCoOrdinates[0])
						&& !StringUtils.isBlank(pointCoOrdinates[1])) {
					Double x = Double.parseDouble(pointCoOrdinates[0].trim());
					Double y = Double.parseDouble(pointCoOrdinates[1].trim());
					polygon.addPoint(x.intValue(), y.intValue());
				}
			}
			if (polygon.contains(point)) {
				return pathwayZone;
			}
		}
		return null;
	}

	/*
	 * This method is used to get pathway lines inside a given zone
	 */
	private List<PathwayLineEntity> getPathwayLinesInZone(MapEntity mapEntity, List<PathwayPointEntity> mapPointList,
			PathwayZoneEntity pathwayZone) {
		List<PathwayLineEntity> zonePathwayLineList = new ArrayList<>();
		if (StringUtils.isBlank(pathwayZone.getPolygon())) {
			return zonePathwayLineList;
		}
		Polygon polygon = new Polygon();
		String[] pointArray = pathwayZone.getPolygon().split(";");
		for (String p : pointArray) {
			String[] pointCoOrdinates = p.split(",");
			if (pointCoOrdinates.length == 2 && !StringUtils.isBlank(pointCoOrdinates[0])
					&& !StringUtils.isBlank(pointCoOrdinates[1])) {
				Double x = Double.parseDouble(pointCoOrdinates[0].trim());
				Double y = Double.parseDouble(pointCoOrdinates[1].trim());
				polygon.addPoint(x.intValue(), y.intValue());
			}
		}
		List<PathwayPointEntity> zonePathwayPointList = new ArrayList<>();
		for (PathwayPointEntity pathwayPointEntity : mapPointList) {
			if (polygon.contains(pathwayPointEntity.getxCord(), pathwayPointEntity.getyCord())) {
				zonePathwayPointList.add(pathwayPointEntity);
			}
		}
		for (PathwayPointEntity pathwayPointEntity : zonePathwayPointList) {
			for (PathwayPointEntity pathwayPointEntity1 : zonePathwayPointList) {
				List<PathwayLineEntity> pathwayLineEntityList = pathwayLineRepository
						.findByStartPointEntityAndEndPointEntityAndMapByMapId(pathwayPointEntity, pathwayPointEntity1,
								mapEntity);
				if (pathwayLineEntityList == null || pathwayLineEntityList.size() == 0) {
					pathwayLineEntityList = pathwayLineRepository.findByStartPointEntityAndEndPointEntityAndMapByMapId(
							pathwayPointEntity1, pathwayPointEntity, mapEntity);
				}
				if (pathwayLineEntityList != null && pathwayLineEntityList.size() > 0) {
					for (PathwayLineEntity pathwayLineEntity : pathwayLineEntityList) {
						List<PathwayObstacleEntity> obstacleList = pathwayObstacleRepository
								.findByPathwayLineEntity(pathwayLineEntity);
						if (obstacleList == null || obstacleList.size() == 0) {
							zonePathwayLineList.add(pathwayLineEntity);
						}
					}
				}
			}
		}
		return zonePathwayLineList;
	}

	/*
	 * This method is used to get nearest path line to given tag
	 */
	private PathwayLineEntity getNearestPathLine(TagEntity tagEntity, List<PathwayLineEntity> plEntityList) {
		MapEntity sourceMap = mapRepository.findById(tagEntity.getLastMapId());
		Point2D tagPoint = new Point2D.Double(tagEntity.getLastX(), tagEntity.getLastY());
		PathwayLineEntity pathwayLineEntity = null;
		double prevDistance = 0.0;
		int i = 0;
		for (PathwayLineEntity pathLineEntry : plEntityList) {
			Point2D closestPoint = PathwayUtils.getClosestPointOnSegment(pathLineEntry.getStartPointEntity(),
					pathLineEntry.getEndPointEntity(), tagPoint);
			double curDistance = getDistanceBetweenTwoPoints(tagPoint, closestPoint);
			if (i == 0 || (curDistance < prevDistance)) {
				pathwayLineEntity = pathLineEntry;
				prevDistance = curDistance;
			}
			i++;
		}
		return pathwayLineEntity;
	}

	/*
	 * This method is used to get angle between 2 points
	 */
	private double getAngle(Point2D p1, Point2D p2) {
		double angle = Math.toDegrees(Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));

		if (angle < 0) {
			angle += 360;
		}

		return angle;
	}

	/*
	 * This method is used to convert node value to double
	 */
	private Double nodeValueToDouble(Object nodeValue) {
		if (nodeValue instanceof Double) {
            return (Double)nodeValue;
        } else if (nodeValue instanceof Long) {
			return ((Long) nodeValue).doubleValue();
		} else {
            return Double.parseDouble("" + nodeValue);
        }
	}

	/*
	 * This method is used to get pathway lines for given map
	 */
	private List<PathwayLineEntity> getPathwayLines(Long mapId) {
		return pathwayLineRepository.findAvailablePathwayLines(mapId);
	}

	/*
	 * This method is used to get pathway nodes for given map
	 */
	private List<PathwayPointEntity> getNodes(Long mapId) {
		MapEntity mapEntity = new MapEntity();
		mapEntity.setId(mapId);
		return pathwayPointRepository.findByMapByMapId(mapEntity);

	}

	/*
	 * This method is used to get difference between 2 double points
	 */
	private double getDifference(double d1, double d2) {
		if (d1 > d2)
			return d1 - d2;
		else
			return d2 - d1;

	}

	/*
	 * This method is used to get direction between points
	 */
	private int getDirection(Point2D p1, Point2D p2, Point2D p3) {
		int s = (int) ((p2.getY() - p1.getY()) * p3.getX() + (p1.getX() - p2.getX()) * p3.getY()
				+ (p2.getX() * p1.getY() - p1.getX() * p2.getY()));
		if (s < 0)
			return -1;
		else if (s > 0)
			return 1;
		else
			return 0;

	}

	/*
	 * This method is used to get distance between 2 points
	 */
	/*public double getDistanceBetweenPoints(Point2D p1, Point2D p2, double scale) {
           
        return p1.distance(p2) * scale;
    }*/

	/*
	 * This method is used to get distance between 2 points in a line
	 */
	private double getDistanceBetweenTwoPoints(Point2D point1, Point2D point2) {
        return point1.distance(point2);
	}

	/*
	 * This method is used to get zone points based polygon string
	 */
	public Point2D[] getZonePoints(String polygon) {
		List<Point2D> pointsList = new ArrayList<Point2D>();
		String[] pointArray = polygon.split(";");
		//int i = 0;
		for (String p : pointArray) {
			String[] pointCoOrdinates = p.split(",");
			if (pointCoOrdinates.length == 2 && !StringUtils.isBlank(pointCoOrdinates[0])
					&& !StringUtils.isBlank(pointCoOrdinates[1])) {
				pointsList.add(new Point2D.Double(Double.parseDouble(pointCoOrdinates[1].trim()),
						Double.parseDouble(pointCoOrdinates[0].trim())));
			//	i++;
			}
		}
		Point2D[] zonePoints = new Point2D[pointsList.size()];
		return pointsList.toArray(zonePoints);

	}

	public ShortestPathResponse getShortestPathForAssetsAndZones(UserEntity userEntity,String assetsAndZones) throws JSONException, JsonParseException, JsonMappingException, IOException
	
	{ 
		List<TagEntity> selectedAssetsandZones = new ArrayList<>();
		List<Long> assetParsed =new ArrayList<>();
		int totalAssetsZones;
		ShortestPathResponse shortestPathResponse = new ShortestPathResponse();
		UserTagRelationEntity userTagRelationEntity = userTagRelationRepository.findByUserByUserId(userEntity);
		if (userTagRelationEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.MAP_NOT_AVAILABLE);
			return shortestPathResponse;
		}

		TagEntity sourceTagEntity = tagRepository.findById(userTagRelationEntity.getTagId());
	

		MapEntity sourceMapEntity = mapRepository.findById(sourceTagEntity.getLastMapId());
		if (sourceMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.SOURCE_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		org.json.JSONObject request=new org.json.JSONObject(assetsAndZones);
		ObjectMapper mapper = new ObjectMapper();
		List<String> assets = mapper.readValue(request.getString("assets"), new TypeReference<List<String>>(){});
		org.json.JSONArray zones = request.getJSONArray("zones");
		List<org.json.JSONObject> zonesList = new ArrayList<>();
		for (int k = 0; k < zones.length(); k++) {
			zonesList.add(zones.getJSONObject(k));	
		}
		List<Map<String, Object>> assetsZonesLocation  = assetService.getAssetsAndZonesLocations(zonesList, assets);
		List<Map<String, Object>> assetsLoc =(List<Map<String, Object>>) assetsZonesLocation.get(1).get("assets");
		List<Map<String, Object>> assetsInAnotherMap=assetsLoc.stream().filter(z->!z.get("mapId").toString().equalsIgnoreCase(sourceMapEntity.getId().toString())).collect(Collectors.toList());
		assetsLoc = assetsLoc.stream().filter(z->z.get("mapId").toString().equalsIgnoreCase(sourceMapEntity.getId().toString())).collect(Collectors.toList());
		List<Map<String, Object>> zonesLoc =(List<Map<String, Object>>) assetsZonesLocation.get(0).get("zones");
		//List<Map<String, Object>> zonesInAnotherMap = zonesLoc.stream().filter(z->!z.get("mapId").toString().equalsIgnoreCase(sourceMapEntity.getId().toString())).collect(Collectors.toList());
		zonesLoc = zonesLoc.stream().filter(z->z.get("mapId").toString().equalsIgnoreCase(sourceMapEntity.getId().toString())).collect(Collectors.toList());
		/*long nearestAssetId = 0;
		double minDistanceForAsset = Double.MAX_VALUE;
		long nearestZoneId = 0; 
		double minDistanceForZone = Double.MAX_VALUE;*/
		selectedAssetsandZones.add(sourceTagEntity);// it should be in f/m
		for (Map<String, Object> asset : assetsLoc) {
			TagEntity tagEntity = new TagEntity();
			tagEntity.setAssetId(Long.parseLong(asset.get("assetId").toString()));
			tagEntity.setLastX(Double.parseDouble(asset.get("lastX").toString()));
			tagEntity.setLastY(Double.parseDouble(asset.get("lastY").toString()));
			tagEntity.setLastMapId(Long.parseLong(asset.get("mapId").toString()));
			tagEntity.setZoneId(null);
			selectedAssetsandZones.add(tagEntity);				
		}
		for (Map<String, Object> zone : zonesLoc) {
			TagEntity tagEntity = new TagEntity();
			tagEntity.setAssetId(Long.parseLong(zone.get("zoneId").toString()));
			tagEntity.setLastX(Double.parseDouble(zone.get("lastX").toString()));
			tagEntity.setLastY(Double.parseDouble(zone.get("lastY").toString()));
			tagEntity.setLastMapId(Long.parseLong(zone.get("mapId").toString()));
			tagEntity.setZoneId(Long.parseLong(zone.get("zoneId").toString()));
			selectedAssetsandZones.add(tagEntity);
		}
		totalAssetsZones = selectedAssetsandZones.size();
		// nearest asset/zone
		double distance = 0;
		int tempPosition=0;
		int currentPosition = 0;
		double minDistance = Double.MAX_VALUE;
		TagEntity nearestAsset = new TagEntity();
		TagEntity sourceStartPoint = new TagEntity();
		while (tempPosition < totalAssetsZones) {
			ShortestPathResponse nearestShortestPath = new ShortestPathResponse();
			ShortestPathResponse currentPath = null;
			for (int k = 0; k < totalAssetsZones; k++) {
				System.out.println("current position::before"+currentPosition);
				TagEntity source = selectedAssetsandZones.get(currentPosition);
				TagEntity destiny = selectedAssetsandZones.get(k);
				System.out.println("current position::after"+currentPosition);
				if (currentPosition != k && !(assetParsed.contains(source.getAssetId()))) {
					currentPath = getShortestPathBetweenTwoPoints(source, destiny);
					if (!currentPath.getStatus().equalsIgnoreCase(PathwayUtils.Status.FAIL)) {
						if (currentPath.getDistance() < minDistance) {
							minDistance = currentPath.getDistance();
							logger.info("copying properties");
							logger.info("current shortest path>>>>:"+currentPath.toString());
							BeanUtils.copyProperties(currentPath,nearestShortestPath);
							BeanUtils.copyProperties(destiny,nearestAsset);
							BeanUtils.copyProperties(source,sourceStartPoint);
							
						}
					}
				}
			}

		/*	if(nearestShortestPath.getStatus() ==null)
			{
				logger.info("nearest shortest path is null");
				logger.info("current path::"+currentPath.toString());
				BeanUtils.copyProperties(currentPath,nearestShortestPath);
			}*/
			System.out.println("nearest shortest path::::"+nearestShortestPath.toString());
			if (!(nearestShortestPath.getStatus() == null
					|| nearestShortestPath.getStatus().equalsIgnoreCase(PathwayUtils.Status.FAIL))) {
				logger.info("inside if cond");
				List<Point2D> endPoints = shortestPathResponse.getEndpoints();
				if(shortestPathResponse.getDistance()!=null)
					distance = shortestPathResponse.getDistance();
				distance += nearestShortestPath.getDistance();
				shortestPathResponse.setDistance(distance);
				if (endPoints == null) {
					endPoints = new ArrayList<>();
				}
				endPoints.add(nearestShortestPath.getEndpoint());
				//nearestShortestPath.setEndpoints(endPoints); // shortestPathResponse
				List<List<JSONObject>> pathPoints = shortestPathResponse.getPathpointsList();
				if (pathPoints == null) {
					pathPoints = new ArrayList<>();
				}
				List<JSONObject> pathPoint = nearestShortestPath.getPathpoints();
				if (pathPoint == null) {
					pathPoint=new ArrayList<>();
				}
				pathPoints.add(pathPoint);
			//	nearestShortestPath.setPathpointsList(pathPoints); // shortestPathResponse
				List<JSONObject> pathPointsList = shortestPathResponse.getPathpoints();
				if (pathPointsList == null) {
					pathPointsList = new ArrayList<>();
				}
				pathPointsList.addAll(nearestShortestPath.getPathpoints());
				shortestPathResponse.setPathpoints(pathPointsList); 
				currentPosition = selectedAssetsandZones.indexOf(nearestAsset);
				logger.info("current position::"+currentPosition);
				logger.info("asset parsed::"+selectedAssetsandZones.get(sourceStartPoint).getAssetId());
				assetParsed.add(selectedAssetsandZones.get(sourceStartPoint).getAssetId());
			//	BeanUtils.copyProperties(nearestShortestPath,shortestPathResponse);
			} else {
				// no path found
				logger.info("no path found");
			}
			tempPosition++;
		}
		List<String> noPathAssets = shortestPathResponse.getNoPathAssets();
		List<String> noPathZones = shortestPathResponse.getNoPathZones();
		if (noPathAssets == null) {
			noPathAssets = new ArrayList<>();
		}
		if (noPathZones == null) {
			noPathZones = new ArrayList<>();
		}
		for (int k = 0; k < totalAssetsZones; k++) {
			if (!assetParsed.contains(selectedAssetsandZones.get(k).getAssetId())) {
				if (selectedAssetsandZones.get(k).getZoneId() != null && (selectedAssetsandZones.get(k).getZoneId()
						.longValue() == selectedAssetsandZones.get(k).getAssetId().longValue())) {
					if (!noPathZones
							.contains(zoneRepository.findOne(selectedAssetsandZones.get(k).getZoneId()).getZoneName()))
						noPathZones
								.add(zoneRepository.findOne(selectedAssetsandZones.get(k).getZoneId()).getZoneName());
				}
				else if (selectedAssetsandZones.get(k).getAssetId() != null && (sourceTagEntity.getAssetByAssetId()
						.getId().longValue() != selectedAssetsandZones.get(k).getAssetId().longValue())) {
					if (!noPathAssets.contains(selectedAssetsandZones.get(k).getAssetId().toString()))
						noPathAssets
								.add(assetRepository.findOne(selectedAssetsandZones.get(k).getAssetId()).getAssetNum());
				}
			}
		}
		shortestPathResponse.setNoPathAssets(noPathAssets);	
		shortestPathResponse.setNoPathZones(noPathZones);	
		// check whether there are any assets/zones in another map
		ShortestPathResponse exitShortest = null;
		if(assetsInAnotherMap.size()>0)
		{ 
			TagEntity source = selectedAssetsandZones.get(currentPosition);
			TagEntity destination = new TagEntity();
			
			destination.setAssetId(Long.parseLong(assetsInAnotherMap.get(0).get("assetId").toString()));
			destination.setLastX(Double.parseDouble(assetsInAnotherMap.get(0).get("lastX").toString()));
			destination.setLastY(Double.parseDouble(assetsInAnotherMap.get(0).get("lastY").toString()));
			destination.setLastMapId(Long.parseLong(assetsInAnotherMap.get(0).get("mapId").toString()));
			exitShortest = getShortestPathBetweenTwoPoints(source, destination);
			if(!(exitShortest.getStatus()==null && exitShortest.getStatus().equalsIgnoreCase(PathwayUtils.Status.FAIL)))
			{
				if(shortestPathResponse.getStatus() == null)
				{
					BeanUtils.copyProperties(exitShortest, shortestPathResponse);
				}
				else {
				System.out.println("shortest path::::"+shortestPathResponse.toString());
				List<Point2D> endPoints = shortestPathResponse.getEndpoints();
				if(endPoints == null)
				{
					endPoints = new ArrayList<>();
				}
				if(shortestPathResponse.getStatus() == null)
				{
					distance = 0.0;
				}
				distance = shortestPathResponse.getDistance();
				distance += exitShortest.getDistance();
				shortestPathResponse.setDistance(distance);
				endPoints.add(exitShortest.getEndpoint());
				shortestPathResponse.setEndpoints(endPoints);
				List<List<JSONObject>> pathPoints = shortestPathResponse.getPathpointsList();
				if(pathPoints == null)
				{
					pathPoints = new ArrayList<>();
				}
				List<JSONObject> pathPoint = exitShortest.getPathpoints();
				if(pathPoint == null)
				{
					pathPoint = new ArrayList<>();
				}
				pathPoints.add(pathPoint);	
				shortestPathResponse.setPathpointsList(pathPoints);
				//BeanUtils.copyProperties(shortestPathResponse,exitShortest);
				assetParsed.add(selectedAssetsandZones.get(currentPosition).getAssetId());
				List<JSONObject> pathPointsList = shortestPathResponse.getPathpoints();
				if(pathPointsList == null)
				{
					pathPointsList = new ArrayList<>(); 
				}
				pathPointsList.addAll(exitShortest.getPathpoints());
				shortestPathResponse.setPathpoints(pathPointsList);
				//currentPosition = selectedAssetsandZones.indexOf(selectedAssetsandZones.get(currentPosition));
			}
			}
		}
		// logger.info("asset loc size:"+assetsLoc.size());
		logger.info("shortest path response:"+shortestPathResponse.toString());
		return shortestPathResponse;
	}
	
	/*
	 * This method is used to get path between two locations
	 */
	public ShortestPathResponse getShortestPathBetweenTwoPoints(TagEntity sourceTagEntity, TagEntity destTagEntity) {
		
		System.out.println("source tag::x::"+sourceTagEntity.getLastX() + " y::"+sourceTagEntity.getLastY() );
		System.out.println("destiny tag::x::"+destTagEntity.getLastX() + " y ::"+destTagEntity.getLastY());
		ShortestPathResponse shortestPathResponse = new ShortestPathResponse();

		MapEntity sourceMapEntity = mapRepository.findById(sourceTagEntity.getLastMapId());
		if (sourceMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.SOURCE_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		MapEntity destMapEntity = mapRepository.findById(destTagEntity.getLastMapId());
		if (destMapEntity == null) {
			shortestPathResponse.setStatus(PathwayUtils.Status.FAIL);
			shortestPathResponse.setMessage(PathwayUtils.ErrorMeesages.DEST_MAP_NOT_FOUND);
			return shortestPathResponse;
		}
		PathwayZoneEntity sourceZoneEntity = getPathwayZoneByPoint(sourceMapEntity, sourceTagEntity);
		PathwayZoneEntity destZoneEntity = getPathwayZoneByPoint(destMapEntity, destTagEntity);

		if (sourceMapEntity.getId().equals(destMapEntity.getId())) {
			return getShortestPathForSingleMap(shortestPathResponse, sourceTagEntity, destTagEntity, sourceZoneEntity,
					destZoneEntity, sourceMapEntity, destMapEntity);
			
		} else {
			return getShortestPathForExit(shortestPathResponse, sourceMapEntity, destMapEntity, sourceTagEntity,
					destTagEntity, sourceZoneEntity, destZoneEntity);
		}
	}
}