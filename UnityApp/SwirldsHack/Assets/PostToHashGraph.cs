using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using System;
using System.Globalization;

public class PostToHashGraph : MonoBehaviour {

	// Use this for initialization
	void Start () {
		Debug.Log("Lance - Start");

		Input.location.Start();
	}
	
	// Update is called once per frame
	void Update () {
	}

	// Called when button is clicked
	public void MyOnClick() {
		Debug.Log("Lance - MyOnClick - Attempting localhost call...");
		StartCoroutine( SendPostRequest ("http://localhost:9111", "http://b9c99ed0.ngrok.io") );
	}
		
	public IEnumerator SendPostRequest(string url, string fallbackUrl) {
		Debug.Log("Lance - PostToHashGraph url = " + url);

		print("Location: " + Input.location.lastData.latitude + " " + 
			Input.location.lastData.longitude + " " + 
			Input.location.lastData.timestamp);

		var latOrDefault = Input.location.lastData.latitude == 0 ? 37.7780897 : Input.location.lastData.latitude;
		var lonOrDefault = Input.location.lastData.longitude == 0 ? -122.3846353 : Input.location.lastData.longitude;

		var t = DateTime.UtcNow - new DateTime(1970, 1, 1);
		var secondsSinceEpoch = (int)t.TotalSeconds;

		var postData = @"{";
		postData += "\"phoneNumber\": \"16464092810\",\n";
		postData += "\"name\": \"Ina Yosun\",\n";
		postData += "\"latitude\": \"" + latOrDefault + "\",\n";
		postData += "\"longitude\": \"" + lonOrDefault + "\",\n";
		postData += "\"type\": \"person\",\n";
		postData += "\"crisis\": \"fire\",\n";
		postData += "\"startTime\": " + secondsSinceEpoch + ",\n";
		postData += "\"endTime\": -1,\n";
		postData += "\"status\": \"open\"\n";
		postData += "}";

		var rawData = System.Text.Encoding.UTF8.GetBytes(postData);

		Hashtable headers = new Hashtable();
		WWW www = new WWW(url, rawData, headers);
		yield return www;
		Debug.Log("Lance - www result: " + www.text);

		var isSuccess = String.IsNullOrEmpty (www.error);
		if (!isSuccess) {
			Debug.Log ("Lance - MyOnClick - call failed");
			if (fallbackUrl != null) {
				StartCoroutine (SendPostRequest (fallbackUrl, null));
			}
		} else {
			Debug.Log ("Lance - MyOnClick - call succeeded");
		}			

	}

}
