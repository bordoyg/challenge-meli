package ar.com.meli.challenge.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;


@RestController
@RequestMapping("/api")
public class ChallengeController {

	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ChallengeController.class);
	private static String COLLECTION_NAME="weather";
	private static String CLIMA_LLUVIA="lluvia";
	private static String CLIMA_SEQUIA="sequia";
	private static String CLIMA_OPTIMO="optimo";
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	int iX=0;
	int iY=1;
	int r1=500;
	int r2=2 * r1;
	int r3=4 * r1;
	
	double[] sun= {0,0};
	double[] p1= {r1,0};
	double[] p2= {r2,0};
	double[] p3= {r3,0};
	
	double[][] s1= {p1, p2};
	double[][] s2= {p2, p3};
	double[][] s3= {p3, p1};
	
	int w1=1;
	int w2=-5;
	int w3=3;
	
	double yearVulcano=Math.abs((360 * 1 / w2));
	double tMax=10 *yearVulcano;
	int t=0;
	
	@RequestMapping(value = "/clima", method = RequestMethod.GET)
	public ResponseEntity<?> weatherByDay(@RequestParam("dia") Integer dia) {
		try {
			LOGGER.info("Init weatherByDay");
			Query searchUserQuery = new Query(Criteria.where("t").is(dia));
			DBObject weatherByDay=mongoTemplate.findOne(searchUserQuery, DBObject.class, COLLECTION_NAME);
			
			if(weatherByDay==null) {
				return new ResponseEntity<DBObject>(weatherByDay, HttpStatus.NOT_FOUND);
			}
			
			BasicDBObject result=new BasicDBObject();
			result.put("dia", weatherByDay.get("t"));
			if(Boolean.parseBoolean(weatherByDay.get("aligned-with-sun").toString())){
				result.put("clima", CLIMA_SEQUIA);
			}else{
				if(Boolean.parseBoolean(weatherByDay.get("aligned").toString())){
					result.put("clima", CLIMA_OPTIMO);
				}	
			}
			
			if(Boolean.parseBoolean(weatherByDay.get("sun-in-area").toString())){
				result.put("clima", CLIMA_LLUVIA);
				result.put("max", weatherByDay.get("max-perimeter"));
			}
			
			return new ResponseEntity<DBObject>(result, HttpStatus.OK);
		}catch(Exception e) {
			LOGGER.info(e.getMessage(), e);
			return new ResponseEntity<Exception>(e, HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	@RequestMapping(value = "/create", method = RequestMethod.POST)
	public ResponseEntity<?> createWeather() {
		try {
			LOGGER.info("Init createWeather");
			mongoTemplate.dropCollection(COLLECTION_NAME);
			
			List<BasicDBObject> weathers=createWeatherByDays();
			for(DBObject weather : weathers){
				mongoTemplate.save(weather, COLLECTION_NAME);	
			}
			
			return new ResponseEntity<Object>(HttpStatus.CREATED);
		}catch(Exception e) {
			LOGGER.info("t = " + t);
			LOGGER.info("p1 = " + p1);
			LOGGER.info("p2 = " + p2);
			LOGGER.info("p3 = " + p3);
			LOGGER.info("s1 = " + s1);
			LOGGER.info("s2 = " + s2);
			LOGGER.info("s3 = " + s3);
			LOGGER.info(e.getMessage(), e);
			return new ResponseEntity<Exception>(e, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	private List<BasicDBObject> createWeatherByDays() throws Exception{
		List<BasicDBObject> weathers=new ArrayList<BasicDBObject>();
		
		for(t=0; t<tMax; t++) {
			p1=positionByDay(p1, r1, w1, t);
			p2=positionByDay(p2, r2, w2, t);
			p3=positionByDay(p3, r3, w3, t);
			s1[0]= p1;
			s1[1]= p2;
			s2[0]= p2;
			s2[1]= p3;
			s3[0]= p3;
			s3[1]= p1;
			
			BasicDBObject weather=new BasicDBObject();

			weather.put("t",t);
			weather.put("p1",p1);
			weather.put("p2",p2);
			weather.put("p3",p3);
			weather.put("aligned",isAligned());
			weather.put("aligned-with-sun",isAlignedWithSun());
			weather.put("sun-in-area",isSunIntoArea());
			weather.put("max-perimeter",isMaxPerimeter());

			weathers.add(weather);
		}
		
		return weathers;
	}
	
	private boolean isAligned() {
		//planets aligned if m1=m2=m3 with m(i) like incline side(i)
		//m(i)=(y(2)-y(1))/(x(2)-x(1))
		
		double m1=incline(s1[0], s1[1]);
		double m2=incline(s2[0], s2[1]);
		double m3=incline(s3[0], s3[1]);
		
		return m1==m2 && m2==m3;
		
	}
	private boolean isAlignedWithSun() {
		//planets aligned with sun if position vector of planets is aligned
		//m(i)=(y(2)-y(1))/(x(2)-x(1))
		
		double m1=incline(sun, p1);
		double m2=incline(sun, p2);
		double m3=incline(sun, p3);
		
		return m1==m2 && m2==m3;
	}
	private boolean isSunIntoArea() {
		//the sun is in the area of the triangle if two sides intersect the x axis with opposite sign
		
		Double x1=intersectionX(s1);
		Double x2=intersectionX(s2);
		Double x3=intersectionX(s3);
		
		if(x1==null && x2==null && x3==null) {
			return false;
		}	
			
		if(x1==null) {
			return between(0, x2, x3);
		}
		if(x2==null) {
			return between(0, x1, x3);
		}
		if(x3==null) {
			return between(0, x1, x2);
		}
		return false;
	}
	private boolean isMaxPerimeter() {
		//is the max perimeter when the distance between the planets is maximum, 
		//that is, 120 ° angles are formed between them
		//P1 . P2 = |P1||P2|cos @
		//P1 . P2 = x(1)x(2) + y(1)y(2)
		
		//cos 120=P1 . P2 / |P1||P2|
		//cos 120=(x(1)x(2) + y(1)y(2)) / |P1||P2|
		
		//Is max perimeter if cos 120=P1 . P2 / |P1||P2| =P2 . P3 / |P2||P3|
		double[][]P1= {p1, sun};
		double[][]P2= {p2, sun};
		double[][]P3= {p3, sun};
		
		double cos120=Math.cos(Math.toRadians(120));
		double cosA=(p1[iX]*p2[iX] + p1[iY]*p2[iY])/(module(P1)*module(P2));
		double cosB=(p2[iX]*p3[iX] + p2[iY]*p3[iY])/(module(P2)*module(P3));
		
		cosA=Math.round(cosA * 100.0) / 100.0;
		cosB=Math.round(cosB * 100.0) / 100.0;
		cos120=Math.round(cos120 * 100.0) / 100.0;
		
		return cos120 == cosA && cos120 == cosB ;
		
	}
	
	private double[] positionByDay(double[]planet, int radio, int velocity, int day) {
		double[] positionPlanet= {0,0};
		positionPlanet[iX]=(radio * Math.cos(Math.toRadians(velocity * day)));
		positionPlanet[iY]=(radio * Math.sin(Math.toRadians(velocity * day)));
		return positionPlanet;
	}
	private double incline(double[] origin, double[] target) {
		//m=(y(2)-y(1))/(x(2)-x(1))
		double m=0;
		m=(target[iY] - origin[iY])/(target[iX] - origin[iX]);
		return m;
	}
	private double[] side(double[] origin, double[] target) {
		double[] side= {target[iX] - origin[iX], target[iY] - origin[iY]};
		return side;
	}
	private double module(double[][] side) {
		//Module:
		//|V|=Math.sqrt(v(i) * v(i) + v(j) * v(j))
		double[] vSide=side(side[0], side[1]);
		double module=Math.sqrt(vSide[iX] * vSide[iX] + vSide[iY] * vSide[iY]);
		return module;
	}
	private double perimeter() {
		double perimeter=module(s1) + module(s2) + module(s3);
		return perimeter;
	}
	private Double intersectionX(double[][] side) {
		//side:
		//        (y-y(1)) = m(x-x(1))
		//      (y-y(1))/m = (x-x(1))
		// (y-y(1))/m + x(1) = x
		
		//intersection with x axis => y=0
		// -y(1)/m + x(1) = x with x into [x(1), x(2)]
		
		Double intersection=null;
		double m=incline(side[0], side[1]);
		try {
			intersection=-side[0][iY] / m + side[0][iX] ;
		}catch(Exception e) {
			LOGGER.info(e.getMessage(), e);
		}
		
		if(intersection!=null) {
			if(between(intersection, side[0][iX], side[1][iX])) {
				return intersection;
			}	
		}
		return null;
	}
	private boolean between(double x, double first, double last) {
		if(first >= last) {
			return first >= x && last <= x;
		}else {
			return last >= x && first <= x;
		}
	}
}
