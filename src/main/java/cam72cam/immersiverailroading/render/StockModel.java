package cam72cam.immersiverailroading.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.lwjgl.util.vector.Vector3f;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock.PosRot;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import cam72cam.immersiverailroading.render.obj.OBJModel;
import cam72cam.immersiverailroading.util.VecUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public class StockModel extends OBJModel {
	boolean hasParsedModel = false;

	public StockModel(ResourceLocation modelLoc) throws Exception {
		super(modelLoc);
	}

	public void draw(EntityRollingStock stock) {
		if (stock instanceof LocomotiveSteam) {
			drawSteamLocomotive((LocomotiveSteam) stock);
		} else if (stock instanceof EntityMoveableRollingStock) {
			drawStandardStock((EntityMoveableRollingStock) stock);
		} else {
			draw();
		}
	}

	private void drawStandardStock(EntityMoveableRollingStock stock) {
		if (stock.frontYaw == null || stock.rearYaw == null) {
			draw();
			return;
		}

		EntityRollingStockDefinition def = stock.getDefinition();

		List<String> main = new ArrayList<String>();
		List<String> front = new ArrayList<String>();
		List<String> rear = new ArrayList<String>();

		for (String group : groups()) {
			if (group.contains("BOGEY_FRONT")) {
				front.add(group);
			} else if (group.contains("BOGEY_REAR")) {
				rear.add(group);
			} else {
				main.add(group);
			}
		}

		drawGroups(main);

		GlStateManager.pushMatrix();
		GlStateManager.translate(-def.getBogeyFront(), 0, 0);
		if (!stock.isReverse) {
			GlStateManager.rotate(180 - stock.frontYaw, 0, 1, 0);
		} else {
			GlStateManager.rotate(180 - stock.rearYaw, 0, 1, 0);
		}
		GlStateManager.rotate(-(180 - stock.rotationYaw), 0, 1, 0);
		GlStateManager.translate(def.getBogeyFront(), 0, 0);
		drawGroups(front);
		GlStateManager.popMatrix();

		GlStateManager.pushMatrix();
		GlStateManager.translate(-def.getBogeyRear(), 0, 0);
		if (!stock.isReverse) {
			GlStateManager.rotate(180 - stock.rearYaw, 0, 1, 0);
		} else {
			GlStateManager.rotate(180 - stock.frontYaw, 0, 1, 0);
		}
		GlStateManager.rotate(-(180 - stock.rotationYaw), 0, 1, 0);
		GlStateManager.translate(def.getBogeyRear(), 0, 0);
		drawGroups(rear);
		GlStateManager.popMatrix();
	}

	Set<String> allGroups;

	private void drawSteamLocomotive(LocomotiveSteam stock) {
		if (stock.frontYaw == null || stock.rearYaw == null) {
			draw();
			return;
		}

		LocomotiveSteamDefinition def = stock.getDefinition();

		if (!hasParsedModel) {
			hasParsedModel = true;
			allGroups = groups();

			allGroups = parseDrivingWheels(allGroups);

			switch (def.getValveGear()) {
			case WALSCHAERTS:
				allGroups = parseWalschaerts(allGroups, "LEFT");
				allGroups = parseWalschaerts(allGroups, "RIGHT");
				break;
			case MALLET_WALSCHAERTS:
				break;
			case CLIMAX:
				break;
			case SHAY:
				break;
			}

			allGroups = parseBogies(allGroups);
		}

		drawBogies(stock);
		drawDrivingWheels(stock);

		switch (def.getValveGear()) {
		case WALSCHAERTS:
			List<List<String>> wheels = new ArrayList<List<String>>();
			wheels.addAll(drivingWheels.values());
			List<String> wheel = wheels.get(wheels.size() / 2);
			drawWalschaerts(stock, "LEFT", 0, wheel);
			drawWalschaerts(stock, "RIGHT", -90, wheel);
			break;
		case MALLET_WALSCHAERTS:
			break;
		case CLIMAX:
			break;
		case SHAY:
			break;
		}

		// Draw remaining groups
		drawGroups(allGroups);
	}

	Map<String, List<String>> drivingWheels = new HashMap<String, List<String>>();

	private Set<String> parseDrivingWheels(Set<String> allGroups) {
		Set<String> main = new HashSet<String>();

		for (String group : allGroups) {
			if (group.contains("WHEEL_DRIVER")) {
				String groupName = group.split("[_" + Pattern.quote(".") + "]")[2];
				if (!drivingWheels.containsKey(groupName)) {
					List<String> names = new ArrayList<String>();
					names.add(group);
					drivingWheels.put(groupName, names);
				} else {
					drivingWheels.get(groupName).add(group);
				}
			} else {
				main.add(group);
			}
		}
		return main;
	}

	private void drawDrivingWheels(LocomotiveSteam stock) {
		for (List<String> wheel : drivingWheels.values()) {
			float circumference = heightOfGroups(wheel) * (float) Math.PI;
			float relDist = stock.distanceTraveled % circumference;
			float wheelAngle = 360 * relDist / circumference;
			Vector3f wheelPos = centerOfGroups(wheel);
			GlStateManager.pushMatrix();
			GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
			GlStateManager.rotate(wheelAngle, 0, 0, 1);
			GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
			drawGroups(wheel);
			GlStateManager.popMatrix();
		}
	}

	List<String> frontBogey = new ArrayList<String>();
	Map<String, List<String>> frontBogeyWheels = new HashMap<String, List<String>>();
	List<String> rearBogey = new ArrayList<String>();
	Map<String, List<String>> rearBogeyWheels = new HashMap<String, List<String>>();

	private Set<String> parseBogies(Set<String> allGroups) {
		Set<String> main = new HashSet<String>();

		for (String group : allGroups) {
			if (group.contains("BOGEY_FRONT")) {
				if (group.contains("WHEEL")) {
					String groupName = group.split("[_" + Pattern.quote(".") + "]")[3];
					if (!frontBogeyWheels.containsKey(groupName)) {
						List<String> names = new ArrayList<String>();
						names.add(group);
						frontBogeyWheels.put(groupName, names);
					} else {
						frontBogeyWheels.get(groupName).add(group);
					}
				} else {
					frontBogey.add(group);
				}
			} else if (group.contains("BOGEY_REAR")) {
				if (group.contains("WHEEL")) {
					String groupName = group.split("[_" + Pattern.quote(".") + "]")[3];
					if (!rearBogeyWheels.containsKey(groupName)) {
						List<String> names = new ArrayList<String>();
						names.add(group);
						rearBogeyWheels.put(groupName, names);
					} else {
						rearBogeyWheels.get(groupName).add(group);
					}
				} else {
					rearBogey.add(group);
				}
			} else {
				main.add(group);
			}
		}

		return main;

	}

	private void drawBogies(LocomotiveSteam stock) {
		LocomotiveSteamDefinition def = stock.getDefinition();

		if (frontBogey.size() != 0 && rearBogey.size() != 0) {

			Vector3f frontVec = centerOfGroups(frontBogey);
			Vector3f rearVec = centerOfGroups(rearBogey);

			PosRot frontPos = stock.predictFrontBogeyPosition(-frontVec.x - def.getBogeyFront());
			PosRot rearPos = stock.predictRearBogeyPosition(rearVec.x + def.getBogeyRear());

			GlStateManager.pushMatrix();

			Vec3d frontPosActual = VecUtil.rotateYaw(frontPos, 180 - stock.rotationYaw);
			GlStateManager.translate(frontPosActual.x, frontPosActual.y, frontPosActual.z);

			GlStateManager.rotate(-(180 - stock.rotationYaw + frontPos.getRotation()), 0, 1, 0);
			GlStateManager.translate(-frontVec.x, 0, 0);
			drawGroups(frontBogey);
			for (List<String> wheel : frontBogeyWheels.values()) {
				float circumference = heightOfGroups(wheel) * (float) Math.PI;
				float relDist = stock.distanceTraveled % circumference;
				Vector3f wheelPos = centerOfGroups(wheel);
				GlStateManager.pushMatrix();
				GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
				GlStateManager.rotate(-360 * relDist / circumference, 0, 0, 1);
				GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
				drawGroups(wheel);
				GlStateManager.popMatrix();
			}
			GlStateManager.popMatrix();

			GlStateManager.pushMatrix();

			Vec3d rearPosActual = VecUtil.rotateYaw(rearPos, 180 - stock.rotationYaw);
			GlStateManager.translate(rearPosActual.x, rearPosActual.y, rearPosActual.z);

			GlStateManager.rotate(-(180 - stock.rotationYaw + rearPos.getRotation()), 0, 1, 0);
			GlStateManager.translate(-rearVec.x, 0, 0);
			drawGroups(rearBogey);
			for (List<String> wheel : rearBogeyWheels.values()) {
				float circumference = heightOfGroups(wheel) * (float) Math.PI;
				float relDist = stock.distanceTraveled % circumference;
				Vector3f wheelPos = centerOfGroups(wheel);
				GlStateManager.pushMatrix();
				GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
				GlStateManager.rotate(-360 * relDist / circumference, 0, 0, 1);
				GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
				drawGroups(wheel);
				GlStateManager.popMatrix();
			}
			GlStateManager.popMatrix();
		}
	}

	Map<String, List<String>> connectingRods = new HashMap<String, List<String>>();
	Map<String, List<String>> drivingRods = new HashMap<String, List<String>>();
	Map<String, List<String>> pistonRods = new HashMap<String, List<String>>();
	Map<String, List<String>> crossHeads = new HashMap<String, List<String>>();
	Map<String, List<String>> combinationLevers = new HashMap<String, List<String>>();
	Map<String, List<String>> returnCrankRods = new HashMap<String, List<String>>();
	Map<String, List<String>> returnCranks = new HashMap<String, List<String>>();
	Map<String, List<String>> slottedLinks = new HashMap<String, List<String>>();

	private Set<String> parseWalschaerts(Set<String> allGroups, String section) {
		Set<String> main = new HashSet<String>();

		connectingRods.put(section, new ArrayList<String>());
		drivingRods.put(section, new ArrayList<String>());
		pistonRods.put(section, new ArrayList<String>());
		crossHeads.put(section, new ArrayList<String>());
		combinationLevers.put(section, new ArrayList<String>());
		returnCrankRods.put(section, new ArrayList<String>());
		returnCranks.put(section, new ArrayList<String>());
		slottedLinks.put(section, new ArrayList<String>());

		for (String group : allGroups) {
			if (group.contains("CONNECTING_ROD")) {
				if (group.contains(section)) {
					connectingRods.get(section).add(group);
					continue;
				}
			} else if (group.contains("DRIVING_ROD")) {
				if (group.contains(section)) {
					drivingRods.get(section).add(group);
					continue;
				}
			} else if (group.contains("PISTON_ROD")) {
				if (group.contains(section)) {
					pistonRods.get(section).add(group);
					continue;
				}
			} else if (group.contains("CROSS_HEAD")) {
				if (group.contains(section)) {
					crossHeads.get(section).add(group);
					continue;
				}
			} else if (group.contains("COMBINATION_LEVER")) {
				if (group.contains(section)) {
					combinationLevers.get(section).add(group);
					continue;
				}
			} else if (group.contains("RETURN_CRANK_ROD")) {
				if (group.contains(section)) {
					returnCrankRods.get(section).add(group);
					continue;
				}
			} else if (group.contains("RETURN_CRANK")) {
				if (group.contains(section)) {
					returnCranks.get(section).add(group);
					continue;
				}
			} else if (group.contains("SLOTTED_LINK")) {
				if (group.contains(section)) {
					slottedLinks.get(section).add(group);
					continue;
				}
			}
			main.add(group);
		}
		return main;
	}

	private void drawWalschaerts(LocomotiveSteam stock, String section, int wheelAngleOffset, List<String> wheel) {
		// TODO wheel index param
		float circumference = heightOfGroups(wheel) * (float) Math.PI;
		float relDist = stock.distanceTraveled % circumference;
		float wheelAngle = 360 * relDist / circumference + wheelAngleOffset;

		Vector3f wheelPos = centerOfGroups(wheel);
		Vector3f connRodPos = centerOfGroups(connectingRods.get(section));
		float connRodOffset = connRodPos.x - wheelPos.x;
		Vector3f drivingRodMin = minOfGroup(drivingRods.get(section));
		Vector3f drivingRodMax = maxOfGroup(drivingRods.get(section));
		float drivingRodHeight = drivingRodMax.y - drivingRodMin.y;
		float drivingRodLength = drivingRodMax.x - drivingRodMin.x;
		float drivingRodCenterLength = drivingRodLength - drivingRodHeight;

		Vec3d connRodMovment = VecUtil.fromYaw(connRodOffset, wheelAngle);
		double drivingRodHorizLeft = Math.sqrt(drivingRodCenterLength * drivingRodCenterLength - connRodMovment.z * connRodMovment.z);

		Vector3f pistonMax = maxOfGroup(pistonRods.get(section));
		double pistonDeltaLeft = -pistonMax.x - drivingRodHorizLeft + connRodMovment.x;

		double returnCrankHeight = heightOfGroups(returnCranks.get(section));
		double returnCrankLength = lengthOfGroups(returnCranks.get(section));
		Vector3f returnCrankPos = centerOfGroups(returnCranks.get(section));
		float returnCrankAngle = 180 - 60;

		double returnCrankRodHeight = heightOfGroups(returnCrankRods.get(section));
		double returnCrankRodLength = lengthOfGroups(returnCrankRods.get(section));
		Vector3f returnCrankRodCenter = centerOfGroups(returnCrankRods.get(section));
		Vec3d crankOffset = VecUtil.fromYaw(returnCrankLength - returnCrankHeight, 90 + wheelAngle + returnCrankAngle);

		Vector3f slottedLinkMin = minOfGroup(slottedLinks.get(section));
		float slottedLinkWidth = lengthOfGroups(slottedLinks.get(section));
		Vector3f slottedLinkCenter = centerOfGroups(slottedLinks.get(section));
		
		Vec3d returnCrankRodPos = new Vec3d(connRodMovment.x, connRodMovment.z, 0);
		returnCrankRodPos = returnCrankRodPos.addVector(wheelPos.x, wheelPos.y, returnCrankRodCenter.z);
		returnCrankRodPos = returnCrankRodPos.addVector(crankOffset.x, crankOffset.z, 0);
		// This line is not taking into account the fact that it is attached
		// to a swing arm. Therefore this and the following lines are "close
		// enough", but not quite left
		Vec3d returnCrankRodOffset = new Vec3d(returnCrankRodPos.x - slottedLinkMin.x,
				returnCrankRodPos.y - slottedLinkMin.y - slottedLinkWidth / 2, 0);
		float returnCrankRodAngle = (float) Math.toDegrees(Math.atan2(returnCrankRodOffset.y, returnCrankRodOffset.x));
		Vec3d returnCrankRodActual = VecUtil.fromYaw(returnCrankRodLength - returnCrankHeight, returnCrankRodAngle);
		returnCrankRodActual = new Vec3d(returnCrankRodPos.x - returnCrankRodActual.z,
				returnCrankRodPos.y + returnCrankRodActual.x, 0);
		float slottedLinkAngle = (float) Math
				.toDegrees(Math.atan2(-slottedLinkCenter.x + returnCrankRodActual.x, slottedLinkCenter.y - returnCrankRodActual.y));

		// CONNECTING_ROD_LEFT
		// DRIVING_ROD_LEFT
		GlStateManager.pushMatrix();
		{
			GlStateManager.translate(-connRodOffset, 0, 0);
			GlStateManager.translate(connRodMovment.x, connRodMovment.z, 0);
			drawGroups(connectingRods.get(section));

			GlStateManager.pushMatrix();
			GlStateManager.translate(connRodPos.x, connRodPos.y, connRodPos.z);
			GlStateManager.rotate((float) Math.toDegrees(Math.atan2(connRodMovment.z, drivingRodHorizLeft)), 0, 0, 1);
			GlStateManager.translate(-connRodPos.x, -connRodPos.y, -connRodPos.z);
			drawGroups(drivingRods.get(section));
			GlStateManager.popMatrix();
		}
		GlStateManager.popMatrix();
		// RETURN_CRANK_LEFT
		GlStateManager.pushMatrix();
		{
			GlStateManager.translate(connRodMovment.x, connRodMovment.z, 0);
			GlStateManager.translate(wheelPos.x, wheelPos.y, returnCrankPos.z);
			GlStateManager.rotate(wheelAngle + returnCrankAngle, 0, 0, 1);
			GlStateManager.translate(-returnCrankLength / 2 + returnCrankHeight / 2, 0, 0);
			GlStateManager.translate(-returnCrankPos.x, -returnCrankPos.y, -returnCrankPos.z);
			drawGroups(returnCranks.get(section));
		}
		GlStateManager.popMatrix();
		// RETURN_CRANK_ROD_LEFT
		GlStateManager.pushMatrix();
		{
			GlStateManager.translate(returnCrankRodPos.x, returnCrankRodPos.y, returnCrankRodPos.z);
			GlStateManager.rotate(returnCrankRodAngle, 0, 0, 1);
			GlStateManager.translate(-returnCrankRodLength / 2 + returnCrankRodHeight / 2, 0, 0);
			GlStateManager.translate(-returnCrankRodCenter.x, -returnCrankRodCenter.y, -returnCrankRodCenter.z);
			drawGroups(returnCrankRods.get(section));
		}
		GlStateManager.popMatrix();
		// SLOTTED_LINK_LEFT
		GlStateManager.pushMatrix();
		{
			GlStateManager.translate(slottedLinkCenter.x, slottedLinkCenter.y, slottedLinkCenter.z);
			GlStateManager.rotate(slottedLinkAngle, 0, 0, 1);
			GlStateManager.translate(-slottedLinkCenter.x, -slottedLinkCenter.y, -slottedLinkCenter.z);
			drawGroups(slottedLinks.get(section));
		}
		GlStateManager.popMatrix();
		// PISTON_LEFT
		GlStateManager.pushMatrix();
		{
			GlStateManager.translate(pistonDeltaLeft, 0, 0);
			drawGroups(pistonRods.get(section));
			drawGroups(crossHeads.get(section));
			// TODO rotate combination lever
			drawGroups(combinationLevers.get(section));
		}
		GlStateManager.popMatrix();
	}
}
