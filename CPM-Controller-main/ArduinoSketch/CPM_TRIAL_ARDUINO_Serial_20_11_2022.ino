/*
  -- Checks --
  1) While reseting linear actuator we can,
  	a) Close linear actuator.
  	b) Stop linear actuator.
  2) When linear actuator is full actuated or retracted,
  	a) We can stop linear actuator.
  	b) If bluetooth is desconnected then it will reset.
  
  -- Authors --
  1) Adesh Sawant
  2) Aditya Shidlyali
  3) Aditya Ghorpade
  4) Shivani Umarani

  -- Last modification --
  Date: 20/11/2022, Time: 01:05 PM
    
  -- Signals --
  1) RST --> Reset linear actuator
  2) CLS --> Close linear actuator
  3) STP --> Stop linear actuator
  4) RES --> Resume linear actuator
  5) RST1 --> First time reset
*/

int SPEED = 255;  // Linear actuator speed is set to maximum
int RPWM = 10;    // connect Arduino pin 10 to IBT-2 pin RPWM
int LPWM = 11;    // connect Arduino pin 11 to IBT-2 pin LPWM

int MAX_DELAY = 29000;      // max delay for to fully extend and retract linear actuator
int DELAY_INTEGER = 0;      // global integer for input angle
int NO_OF_REPETITIONS = 0;  // global integer for user's number repetitions
bool FLAG = false;

int BT_STATE_PIN = 8;  // State pin of HC05

unsigned int START_TIME_MILLIS;
unsigned int END_TIME_MILLIS;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  pinMode(10, OUTPUT);           // Configure pin 10 as an Output
  pinMode(11, OUTPUT);           // Configure pin 11 as an Output
  pinMode(BT_STATE_PIN, INPUT);  // State pin of HC05
}

void loop() {
  // put your main code here, to run repeatedly:
  String inputString = "-1";
  if (Serial.available() > 0) {
    inputString = Serial.readString();
    if (FLAG == false && inputString == "RST1") {
      resetTheLinearActuator();
      FLAG = true;
      inputString = "-1";
    }

    if (FLAG == false && inputString == "CLS") {
      closeTheLinearActuator();
      inputString = "-1";
    }
    if (FLAG) {
      if (inputString == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
        resetTheLinearActuator();
      } else if (inputString == "CLS") {
        closeTheLinearActuator();
      } else if (inputString != "-1") {
        doExercise(inputString);
      }
    }
  }
}

void doExercise(String inputString) {
  int i = 0;
  String delayString = "", noOfRepetitionsString = "" , cmd = "";

  bool flag = false;

  while (inputString[i] != '\0') {
    if (inputString[i] == ',') {
      flag = true;
      ++i;
    }

    if (flag == false) {
      delayString += inputString[i];
    }

    if (flag == true) {
      noOfRepetitionsString += inputString[i];
    }

    ++i;
  }

  DELAY_INTEGER = delayString.toInt();
  NO_OF_REPETITIONS = noOfRepetitionsString.toInt();

  i = 0;
  while (i < NO_OF_REPETITIONS) {
    
    // STOP for 5 seconds
    analogWrite(RPWM, LOW);
    analogWrite(LPWM, LOW);

    START_TIME_MILLIS = millis();
    END_TIME_MILLIS = millis();

    
    while ((END_TIME_MILLIS - START_TIME_MILLIS) < 5000) {
      
      cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();
      
      if (cmd == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
        DELAY_INTEGER = 0;
        NO_OF_REPETITIONS = 0;
      }
      if (cmd == "STP") {
        START_TIME_MILLIS += StopProcess();
      }
      if (cmd == "CLS")
        closeTheLinearActuator();
      END_TIME_MILLIS = millis();
    }
    
    // Retract for "DELAY_INTEGER" seconds

    analogWrite(RPWM, SPEED);
    analogWrite(LPWM, LOW);

    START_TIME_MILLIS = millis();
    END_TIME_MILLIS = millis();

    while ((END_TIME_MILLIS - START_TIME_MILLIS) < DELAY_INTEGER) {
      
      cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();
        
      if (cmd == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
        DELAY_INTEGER = (millis() - START_TIME_MILLIS);
        resetTheLinearActuator();
      }
      if (cmd == "STP") {

        analogWrite(RPWM, LOW);
        analogWrite(LPWM, LOW);


        START_TIME_MILLIS += StopProcess();

        analogWrite(RPWM, SPEED);
        analogWrite(LPWM, LOW);
      }
      if (cmd == "CLS")
        closeTheLinearActuator();
      END_TIME_MILLIS = millis();
    }

    // STOP for 5 seconds
    analogWrite(RPWM, LOW);
    analogWrite(LPWM, LOW);

    START_TIME_MILLIS = millis();
    END_TIME_MILLIS = millis();

    while ((END_TIME_MILLIS - START_TIME_MILLIS) < 5000) {
      
      cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();
        
      if (cmd == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
        resetTheLinearActuator();
      }
      if (cmd == "STP") {
        START_TIME_MILLIS += StopProcess();
      }
      if (cmd == "CLS")
        closeTheLinearActuator();
      END_TIME_MILLIS = millis();
    }

    // Actuate for "DELAY_INTEGER" seconds
    analogWrite(RPWM, LOW);
    analogWrite(LPWM, SPEED);

    START_TIME_MILLIS = millis();
    END_TIME_MILLIS = millis();

    while ((END_TIME_MILLIS - START_TIME_MILLIS) < DELAY_INTEGER) {
      
      cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();
        
      if (cmd == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
        DELAY_INTEGER = DELAY_INTEGER - (millis() - START_TIME_MILLIS);
        resetTheLinearActuator();
      }

      if (cmd == "STP") {

        analogWrite(RPWM, LOW);
        analogWrite(LPWM, LOW);

        START_TIME_MILLIS += StopProcess();
        
        analogWrite(RPWM, LOW);
        analogWrite(LPWM, SPEED);
      }
      if (cmd == "CLS")
        closeTheLinearActuator();
      END_TIME_MILLIS = millis();
    }

    Serial.flush();
    Serial.print(i + 1);
    Serial.flush();

    ++i;
  }
}

void resetTheLinearActuator() {

  analogWrite(RPWM, LOW);
  analogWrite(LPWM, SPEED);

  String cmd = "";

  if (DELAY_INTEGER != 0) {
    START_TIME_MILLIS = millis();
    END_TIME_MILLIS = millis();
    while ((END_TIME_MILLIS - START_TIME_MILLIS) < (DELAY_INTEGER)) {

      cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();
        
      if (cmd == "CLS")
        closeTheLinearActuator();
	
	  if (cmd == "STP") {

        analogWrite(RPWM, LOW);
        analogWrite(LPWM, LOW);

        START_TIME_MILLIS += StopProcess();
        
        analogWrite(RPWM, LOW);
        analogWrite(LPWM, SPEED);
      }
      
      END_TIME_MILLIS = millis();
    }
  } else {
    delay(MAX_DELAY);
  }
  DELAY_INTEGER = 0;
  NO_OF_REPETITIONS = 0;
  
  analogWrite(RPWM, LOW);
  analogWrite(LPWM, LOW);
}

void closeTheLinearActuator() {

  analogWrite(RPWM, SPEED);
  analogWrite(LPWM, LOW);

  DELAY_INTEGER = 0;
  NO_OF_REPETITIONS = 0;
  FLAG = false;
  delay(MAX_DELAY);

  analogWrite(RPWM, LOW);
  analogWrite(LPWM, LOW);
}


//STOP signal
unsigned int StopProcess() {

  // Serial.println("STOP PROCESS");
  String cmd = "";
  
  unsigned int t1 = millis();
  while (true) {

    cmd = "-1";
      if(Serial.available()>0)
        cmd = Serial.readString();

//    if (cmd == "RST" || digitalRead(BT_STATE_PIN) == LOW) {
//        DELAY_INTEGER = DELAY_INTEGER - START_TIME_MILLIS;
//        resetTheLinearActuator();
//      }

    if (cmd == "CLS")
        closeTheLinearActuator();
     
    if (cmd == "RES")
      return millis() - t1;
  }
}
