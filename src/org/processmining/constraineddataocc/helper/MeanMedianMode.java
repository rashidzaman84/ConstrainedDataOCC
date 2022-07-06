package org.processmining.constraineddataocc.helper;

import java.util.ArrayList;
import java.util.Collections;

public class MeanMedianMode {
	public static Integer findMean(ArrayList<Integer> anArray)
	{	ArrayList<Integer> myArray = anArray;
		int arraySum = 0;
		int arrayAverage = 0;
		for(int x = 0; x < myArray.size() - 1; x++)
			arraySum += myArray.get(x);		
		arrayAverage = arraySum / myArray.size();
		return arrayAverage;
	}
	public static Integer findMedian(ArrayList<Integer> anArray)
	{	ArrayList<Integer> myArray = anArray;
		Collections.sort(myArray);
		int arrayLength = 0;
		int arrayMedian = 0;
		int currentIndex = 0;
		arrayLength = myArray.size();
		if(arrayLength % 2 != 0)
		{	currentIndex = ((arrayLength / 2) + 1);
			arrayMedian = myArray.get(currentIndex - 1);
		}
		else
		{	int indexOne = (arrayLength / 2);
			int indexTwo = arrayLength / 2 + 1;
			int arraysSum = myArray.get(indexOne - 1) + myArray.get(indexTwo - 1);
			arrayMedian = arraysSum / 2;
		}
		return arrayMedian;
	}
	public static ArrayList<Integer> findMode(ArrayList<Integer> anArray)
	{	ArrayList<Integer> arrayOne = anArray;
		ArrayList<Integer> arrayTwo = new ArrayList<Integer>();
		ArrayList<Integer> arrayThree = new ArrayList<Integer>();
		ArrayList<Integer> modes = new ArrayList<Integer>();
		int numOfModes = 0;
		int currentNumber = 0;
		for(int x = 0; x < arrayOne.size(); x++)
		{	currentNumber = arrayOne.get(x);
			for(int y = 0; y < arrayOne.size() - 1; y++)
				if(!isInArray(arrayTwo, currentNumber))
					arrayTwo.add(currentNumber);	
		}
		for(int z = 0; z < arrayTwo.size(); z++)
			arrayThree.add(occuranceCount(arrayOne, arrayTwo.get(z)));
		numOfModes = findGreatestIndex(arrayThree).size();
		for(int u = 0; u < numOfModes; u++)
			modes.add(arrayTwo.get(u));
		return modes;
	
	}
	public static int occuranceCount(ArrayList<Integer> anArray, Integer check)
	{	ArrayList<Integer> originalArray = anArray;
		int occuranceCount = 0;
			for(int y = 0; y < originalArray.size(); y++)
				if(originalArray.get(y) == check)
					occuranceCount++;
		return occuranceCount;
	}
	public static ArrayList<Integer> findGreatestIndex(ArrayList<Integer> anArray)
	{	ArrayList<Integer> myArray = anArray;
		ArrayList<Integer> numOfModes = new ArrayList<Integer>();
		Collections.sort(myArray);
		Collections.reverse(myArray);
		for(int x = 0; x < myArray.size() - 1; x++)
			if(myArray.get(x) == myArray.get(0))
				numOfModes.add(myArray.get(x));
		return numOfModes;
	}
	public static boolean isInArray(ArrayList<Integer> anArray, Integer check)
	{	ArrayList<Integer> originalArray = anArray;
		boolean answer = false;
		for(int x = 0; x < originalArray.size(); x++)
			if(check == originalArray.get(x))
				answer = true;
			else
				answer = false;
		return answer;
	}
}
