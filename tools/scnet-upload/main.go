package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const (
	apiBase      = "https://api.scnet.cn"
	tokenURL     = apiBase + "/api/user/v3/tokens"
	centerURL    = apiBase + "/ac/openapi/v2/center"
	validDays    = "30"
	defaultCover = "cover"
)

// ---- AKSK response (v3 recommended API) ----
type TokenDataV3 struct {
	ClusterID   string `json:"clusterId"`
	ClusterName string `json:"clusterName"`
	Token       string `json:"token"`
}

type TokenResponseV3 struct {
	Code string        `json:"code"`
	Msg  string        `json:"msg"`
	Data []TokenDataV3 `json:"data"`
}

// ---- center info ----
type CenterEfileUrl struct {
	URL     string `json:"url"`
	Enable  string `json:"enable"`
	Version string `json:"version"`
}

type CenterResponse struct {
	Code string `json:"code"`
	Msg  string `json:"msg"`
	Data struct {
		EfileUrls       []CenterEfileUrl `json:"efileUrls"`
		ClusterUserInfo struct {
			UserName string `json:"userName"`
			HomePath string `json:"homePath"`
		} `json:"clusterUserInfo"`
	} `json:"data"`
}

// ---- generic response ----
type ApiResponse struct {
	Code string      `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data"`
}

// ---- share response ----
type ShareData struct {
	ServerCurlLink     string `json:"serverCurlLink"`
	ServerFastransLink string `json:"serverFastransLink"`
	WebLink            string `json:"webLink"`
	ValidTime          string `json:"validTime"`
}

type ShareResponse struct {
	Code string    `json:"code"`
	Msg  string    `json:"msg"`
	Data ShareData `json:"data"`
}

// ---- result types ----
type FileResult struct {
	LocalPath  string `json:"localPath"`
	RemotePath string `json:"remotePath"`
	Error      string `json:"error,omitempty"`
}

type UploadResult struct {
	Files   []FileResult `json:"files"`
	WebLink string       `json:"webLink,omitempty"`
	DirPath string       `json:"dirPath"`
	Success bool         `json:"success"`
	Error   string       `json:"error,omitempty"`
}

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Usage: cloud-drive <file1> [file2 ...]")
		fmt.Fprintln(os.Stderr, "Env vars: SCNET_USER, SCNET_ACCESS_KEY, SCNET_SECRET_KEY, SCNET_REMOTE_DIR")
		os.Exit(1)
	}

	user := os.Getenv("SCNET_USER")
	accessKey := os.Getenv("SCNET_ACCESS_KEY")
	secretKey := os.Getenv("SCNET_SECRET_KEY")
	remoteDir := os.Getenv("SCNET_REMOTE_DIR")

	if user == "" || accessKey == "" || secretKey == "" {
		fmt.Fprintln(os.Stderr, "SCNET_USER, SCNET_ACCESS_KEY, and SCNET_SECRET_KEY env vars are required")
		os.Exit(1)
	}

	if remoteDir == "" {
		remoteDir = "/public/home/" + user + "/msf-friends"
	}

	files := os.Args[1:]
	result := UploadResult{
		Files:   make([]FileResult, 0, len(files)),
		DirPath: remoteDir,
	}

	// Step 1: Get auth token via AKSK (recommended API)
	token, err := getTokenAKSK(user, accessKey, secretKey)
	if err != nil {
		result.Error = fmt.Sprintf("auth failed: %v", err)
		outputAndExit(result, 1)
	}

	// Step 2: Get center info (efileUrls)
	efileURL, homePath, err := getEfileURL(token)
	if err != nil {
		result.Error = fmt.Sprintf("get center info failed: %v", err)
		outputAndExit(result, 1)
	}

	// Use homePath from center info if available for better path
	if homePath != "" && os.Getenv("SCNET_REMOTE_DIR") == "" {
		remoteDir = homePath + "/msf-friends"
		result.DirPath = remoteDir
	}

	client := &http.Client{Timeout: 10 * time.Minute}

	// Step 3: Ensure target directory exists (createParents=true)
	fmt.Fprintf(os.Stderr, "Ensuring directory: %s\n", remoteDir)
	if err := mkdirWithParents(client, efileURL, token, remoteDir); err != nil {
		// 911021 = already exists, which is fine
		if !strings.Contains(err.Error(), "911021") {
			result.Error = fmt.Sprintf("mkdir failed: %v", err)
			outputAndExit(result, 1)
		}
		fmt.Fprintf(os.Stderr, "Directory already exists, continuing\n")
	}

	// Step 4: Upload all files to the same directory
	allSuccess := true
	for _, localPath := range files {
		fr := FileResult{LocalPath: localPath}

		fileName := filepath.Base(localPath)
		remoteFilePath := remoteDir + "/" + fileName
		fr.RemotePath = remoteFilePath

		if err := uploadFile(client, efileURL, token, localPath, remoteDir); err != nil {
			fr.Error = fmt.Sprintf("upload failed: %v", err)
			allSuccess = false
		} else {
			fmt.Fprintf(os.Stderr, "Uploaded: %s\n", localPath)
		}

		result.Files = append(result.Files, fr)
	}

	if !allSuccess {
		result.Error = "some files failed to upload"
		outputAndExit(result, 1)
	}

	// Step 5: Share the entire directory
	fmt.Fprintf(os.Stderr, "Sharing directory: %s\n", remoteDir)
	webLink, err := openShare(client, efileURL, token, remoteDir)
	if err != nil {
		result.Error = fmt.Sprintf("share failed: %v", err)
		outputAndExit(result, 1)
	}
	result.WebLink = webLink
	fmt.Fprintf(os.Stderr, "Shared: %s\n", webLink)

	result.Success = true
	outputAndExit(result, 0)
}

// ---- AKSK authentication ----
func signAKSK(accessKey, timestamp, user, secretKey string) string {
	// Escape JSON special characters
	esc := func(s string) string {
		s = strings.ReplaceAll(s, "\\", "\\\\")
		s = strings.ReplaceAll(s, "\"", "\\\"")
		return s
	}
	dataToSign := fmt.Sprintf(`{"accessKey":"%s","timestamp":"%s","user":"%s"}`,
		esc(accessKey), esc(timestamp), esc(user))

	mac := hmac.New(sha256.New, []byte(secretKey))
	mac.Write([]byte(dataToSign))
	return hex.EncodeToString(mac.Sum(nil))
}

func getTokenAKSK(user, accessKey, secretKey string) (string, error) {
	timestamp := strconv.FormatInt(time.Now().Unix(), 10)
	signature := signAKSK(accessKey, timestamp, user, secretKey)

	req, err := http.NewRequest("POST", tokenURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("user", user)
	req.Header.Set("accessKey", accessKey)
	req.Header.Set("signature", signature)
	req.Header.Set("timestamp", timestamp)

	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("token request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("read token response: %w", err)
	}

	var tr TokenResponseV3
	if err := json.Unmarshal(body, &tr); err != nil {
		return "", fmt.Errorf("parse token response: %w (body: %s)", err, string(body))
	}

	if tr.Code != "0" {
		return "", fmt.Errorf("token API error: code=%s msg=%s", tr.Code, tr.Msg)
	}

	if len(tr.Data) == 0 {
		return "", fmt.Errorf("no token data returned")
	}

	// Prefer clusterId "0" (ac platform token), fallback to first
	for _, d := range tr.Data {
		if d.ClusterID == "0" {
			return d.Token, nil
		}
	}
	return tr.Data[0].Token, nil
}

func getEfileURL(token string) (efileURL string, homePath string, err error) {
	req, err := http.NewRequest("GET", centerURL, nil)
	if err != nil {
		return "", "", err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("token", token)

	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", "", fmt.Errorf("center request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", fmt.Errorf("read center response: %w", err)
	}

	var cr CenterResponse
	if err := json.Unmarshal(body, &cr); err != nil {
		return "", "", fmt.Errorf("parse center response: %w (body: %s)", err, string(body))
	}

	if cr.Code != "0" {
		return "", "", fmt.Errorf("center API error: code=%s msg=%s", cr.Code, cr.Msg)
	}

	homePath = cr.Data.ClusterUserInfo.HomePath

	for _, eu := range cr.Data.EfileUrls {
		if eu.Enable == "true" && eu.URL != "" {
			return eu.URL, homePath, nil
		}
	}

	return "", "", fmt.Errorf("no enabled efile URL found in center response")
}

// ---- mkdir with createParents ----
func mkdirWithParents(client *http.Client, efileURL, token, dirPath string) error {
	u := fmt.Sprintf("%s/efile/openapi/v2/file/mkdir?path=%s&createParents=true",
		strings.TrimRight(efileURL, "/"), dirPath)

	req, err := http.NewRequest("POST", u, nil)
	if err != nil {
		return fmt.Errorf("create mkdir request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("token", token)

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("mkdir request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read mkdir response: %w", err)
	}

	var ar ApiResponse
	if err := json.Unmarshal(respBody, &ar); err != nil {
		return fmt.Errorf("parse mkdir response: %w (body: %s)", err, string(respBody))
	}

	if ar.Code != "0" {
		return fmt.Errorf("mkdir failed: code=%s msg=%s", ar.Code, ar.Msg)
	}

	return nil
}

func uploadFile(client *http.Client, efileURL, token, localPath, remoteDir string) error {
	file, err := os.Open(localPath)
	if err != nil {
		return fmt.Errorf("open local file: %w", err)
	}
	defer file.Close()

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// Add cover field
	if err := writer.WriteField("cover", defaultCover); err != nil {
		return fmt.Errorf("write cover field: %w", err)
	}

	// Add path field
	if err := writer.WriteField("path", remoteDir); err != nil {
		return fmt.Errorf("write path field: %w", err)
	}

	// Add file field
	part, err := writer.CreateFormFile("file", filepath.Base(localPath))
	if err != nil {
		return fmt.Errorf("create form file: %w", err)
	}
	if _, err := io.Copy(part, file); err != nil {
		return fmt.Errorf("copy file content: %w", err)
	}

	if err := writer.Close(); err != nil {
		return fmt.Errorf("close multipart writer: %w", err)
	}

	uploadURL := strings.TrimRight(efileURL, "/") + "/efile/openapi/v2/file/upload"
	req, err := http.NewRequest("POST", uploadURL, body)
	if err != nil {
		return fmt.Errorf("create upload request: %w", err)
	}
	req.Header.Set("token", token)
	req.Header.Set("Content-Type", writer.FormDataContentType())

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("upload request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read upload response: %w", err)
	}

	var ar ApiResponse
	if err := json.Unmarshal(respBody, &ar); err != nil {
		return fmt.Errorf("parse upload response: %w (body: %s)", err, string(respBody))
	}

	if ar.Code != "0" {
		return fmt.Errorf("upload failed: code=%s msg=%s", ar.Code, ar.Msg)
	}

	return nil
}

func openShare(client *http.Client, efileURL, token, filePath string) (string, error) {
	shareURL := fmt.Sprintf("%s/efile/openapi/v2/file/open-share?filePath=%s&validDays=%s",
		strings.TrimRight(efileURL, "/"),
		filePath,
		validDays,
	)

	req, err := http.NewRequest("POST", shareURL, bytes.NewReader([]byte{}))
	if err != nil {
		return "", fmt.Errorf("create share request: %w", err)
	}
	req.Header.Set("token", token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("share request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("read share response: %w", err)
	}

	var sr ShareResponse
	if err := json.Unmarshal(respBody, &sr); err != nil {
		return "", fmt.Errorf("parse share response: %w (body: %s)", err, string(respBody))
	}

	if sr.Code != "0" {
		return "", fmt.Errorf("share failed: code=%s msg=%s", sr.Code, sr.Msg)
	}

	return sr.Data.WebLink, nil
}

func outputAndExit(result UploadResult, exitCode int) {
	output, _ := json.MarshalIndent(result, "", "  ")
	fmt.Println(string(output))
	os.Exit(exitCode)
}
