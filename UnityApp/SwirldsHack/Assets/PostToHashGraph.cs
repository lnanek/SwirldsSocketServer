using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using System;
using System.Globalization;

public class PostToHashGraph : MonoBehaviour {

	// Use this for initialization
	void Start () {
		Debug.Log("Lance - Start");
	}
	
	// Update is called once per frame
	void Update () {
	}

	// Called when button is clicked
	public void MyOnClick() {
		Debug.Log("Lance - MyOnClick");
		StartCoroutine( SendPostRequest () );
	}
		
	public IEnumerator SendPostRequest() {
		Debug.Log("Lance - PostToHashGraph");

		var t = DateTime.UtcNow - new DateTime(1970, 1, 1);
		var secondsSinceEpoch = (int)t.TotalSeconds;

		var postData = @"{";
		postData += "\"phoneNumber\": \"16464092810\",\n";
		postData += "\"name\": \"Ina Yosun\",\n";
		postData += "\"latitude\": \"37.7780897\",\n";
		postData += "\"longitude\": \"-122.3846353\",\n";
		postData += "\"type\": \"person\",\n";
		postData += "\"crisis\": \"fire\",\n";
		postData += "\"startTime\": " + secondsSinceEpoch + ",\n";
		postData += "\"endTime\": -1,\n";
		postData += "\"status\": \"open\"\n";
		postData += "}";

		var rawData = System.Text.Encoding.UTF8.GetBytes(postData);

		string url = "http://localhost:9111";
		//string url = "http://b9c99ed0.ngrok.io";

		Hashtable headers = new Hashtable();
		WWW www = new WWW(url, rawData, headers);
		yield return www;

		Debug.Log("Lance - www result: " + www.text);
	}

}
